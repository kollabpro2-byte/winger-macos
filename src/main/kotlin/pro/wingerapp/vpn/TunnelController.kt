package pro.wingerapp.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Десктопная замена `WingerVpnService`.
 *
 * На Android ядро жило в том же процессе (libbox + VpnService). На Windows/macOS
 * ядро — отдельный процесс `sing-box`, поэтому здесь:
 *
 *  1. поднимается прослойка LTE4/LTE5 (её порт уходит в конфиг как `shimPort`);
 *  2. пишется конфиг sing-box (тун или локальный прокси — см. [coreConfig]);
 *  3. запускается процесс ядра, его вывод пишется в журнал;
 *  4. счётчики ↑/↓ читаются из Clash API самого ядра.
 */
object TunnelController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var core: Process? = null

    @Volatile
    private var shim: Lte4Shim? = null

    @Volatile
    private var trafficJob: Job? = null

    /** Текущая сессия поднята системным демоном, а не нашим процессом. */
    @Volatile
    private var privilegedActive = false

    /** Номер попытки: поздние колбэки старой сессии не должны трогать новую. */
    @Volatile
    private var generation = 0

    /**
     * Порты выбираются при старте: если 2080/9090 заняты (например, осталось
     * ядро от прошлого запуска), берём свободные. Фиксированные порты хороши
     * тем, что их знает пользователь, поэтому предпочитаем их.
     */
    @Volatile
    internal var mixedPort = 2080
        private set

    @Volatile
    private var clashPort = 9090

    private val corePath: File = Platform.file(if (Platform.isWindows) "sing-box.exe" else "sing-box")

    init {
        // При выходе (в том числе Cmd+Q, где onCloseRequest не вызывается)
        // дочерний процесс ядра не должен оставаться жить со своим tun.
        Runtime.getRuntime().addShutdownHook(
            Thread({
                runCatching { shim?.stop() }
                runCatching { core?.destroy() }
            }, "winger-shutdown"),
        )
    }

    fun isRunning(): Boolean = core?.isAlive == true

    /**
     * Поднимает туннель. Ошибки (нет ядра, ядро отвергло конфиг) приводят к
     * понятному сообщению в UI, а не к «включено», которое ничего не делает.
     */
    fun start(xrayJson: String) {
        val gen = ++generation
        TunnelState.clearError()
        scope.launch {
            try {
                stopInternal(notify = false)

                // Установлен системный компонент (root-демон/задача планировщика):
                // tun поднимает он, а приложение только передаёт конфиг. Тогда
                // Cmd+Q и перезапуск приложения туннель не рвут.
                if (usePrivileged()) {
                    startPrivileged(xrayJson, gen)
                    return@launch
                }

                // Иначе — свой процесс. Ядро могло остаться от прошлого запуска
                // (Cmd+Q, падение) и держать порты; иначе будет bind: in use.
                killStaleCore()
                mixedPort = freePort(2080)
                clashPort = freePort(9090)

                val shimPort = startShim()
                val tun = processTun()
                val config = coreConfig(xrayJson, shimPort, tun)
                val path = Platform.file("core.json")
                path.writeText(config)
                VpnLog.add(
                    "старт sing-box, конфиг ${config.length} байт, " +
                        if (tun) "режим tun" else "прокси 127.0.0.1:$mixedPort",
                )

                val binary = resolveBinary()
                val process = ProcessBuilder(binary.absolutePath, "run", "-c", path.absolutePath)
                    .directory(Platform.dataDir)
                    .redirectErrorStream(true)
                    .start()
                core = process
                pipeOutput(process, gen)
                Store.running(true)
                Store.lastConfig(xrayJson)
                TunnelState.serviceAlive = true
                TunnelState.on()
                CoreTraffic.reset()
                startTrafficPolling(gen)

                // Ядро может упасть сразу: ждём пару секунд и честно сообщаем.
                delay(1_500)
                if (gen == generation && !process.isAlive) {
                    val code = runCatching { process.exitValue() }.getOrDefault(-1)
                    TunnelState.fail("ядро не запустилось (код $code), подробности в логе")
                    Store.running(false)
                    TunnelState.serviceAlive = false
                }
            } catch (error: Throwable) {
                VpnLog.add("старт туннеля: ${error.javaClass.simpleName}: ${error.message}")
                TunnelState.fail(error.message ?: "не удалось запустить ядро")
                Store.running(false)
                TunnelState.serviceAlive = false
            }
        }
    }

    /**
     * Восстанавливает туннель после запуска приложения.
     *
     * Если системный демон уже держит туннель (приложение закрывали при
     * работающем VPN), процесс не поднимаем заново — только возвращаем состояние
     * и счётчики. Иначе стартуем сохранённый конфиг.
     */
    fun resumeSaved() {
        if (!Store.running()) return
        val config = Store.lastConfig()
        if (config.isBlank()) return

        val privilegedOn = PrivilegedTunnel.isInstalled() &&
            PrivilegedTunnel.controlFile.let { it.exists() && it.readText().trim() == "on" }
        if (privilegedOn) {
            privilegedActive = true
            TunnelState.serviceAlive = true
            TunnelState.on()
            startTrafficPolling(generation)
            VpnLog.add("туннель уже работает в системном демоне")
            return
        }
        start(config)
    }

    fun stop() {
        generation++
        scope.launch { stopInternal(notify = true) }
    }

    private suspend fun stopInternal(notify: Boolean) {
        if (privilegedActive) {
            PrivilegedTunnel.stop()
            privilegedActive = false
        }
        trafficJob?.cancel()
        trafficJob = null
        runCatching { shim?.stop() }
        shim = null
        val process = core
        core = null
        if (process != null && process.isAlive) {
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        CoreTraffic.reset()
        TunnelState.serviceAlive = false
        Store.running(false)
        Store.clearLastConfig()
        if (notify) TunnelState.off()
        VpnLog.add("туннель остановлен")
    }

    /** Прослойка нужна только для шлюзов Яндекса, но порт стоит копейки. */
    private fun startShim(): Int = try {
        val instance = Lte4Shim(
            mode = Store.lte4Mode(),
            initialStreams = Store.lte4Streams(),
            maxStreams = Store.lte4StreamsDefault(),
            onStreamsChanged = { Store.lte4Streams(it) },
            // На десктопе нет VpnService, защищать сокеты не от чего.
            protect = { true },
        )
        shim = instance
        instance.start()
    } catch (error: Throwable) {
        VpnLog.add("прослойка не поднялась: ${error.message}")
        0
    }

    /** Путь к ядру: нужен установщику системного режима. */
    internal fun coreBinary(): File = resolveBinary()

    /**
     * Гасит ядра, оставшиеся от прошлых запусков.
     *
     * Дочерний процесс не умирает вместе с JVM, если приложение закрыли
     * принудительно (Cmd+Q, падение, kill -9). Такое ядро держит 127.0.0.1:2080
     * и 9090, и следующий старт падает с «bind: address already in use».
     * Ищем только процессы sing-box, запущенные с нашим конфигом.
     */
    private fun killStaleCore() {
        val ourConfig = Platform.file("core.json").absolutePath
        val stale = runCatching {
            ProcessHandle.allProcesses()
                .filter { handle ->
                    val info = handle.info()
                    val command = info.command().orElse("")
                    val args = info.arguments().orElse(emptyArray<String>())
                    command.contains("sing-box") &&
                        (args.any { it.contains(ourConfig) } ||
                            info.commandLine().orElse("").contains(ourConfig))
                }
                .filter { it.pid() != ProcessHandle.current().pid() }
                .toList()
        }.getOrDefault(emptyList())

        if (stale.isEmpty()) return
        stale.forEach { runCatching { it.destroy() } }
        VpnLog.add("остановлено прежних ядер: ${stale.size}")

        // Дать портам освободиться: сразу после destroy bind может не пройти.
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline && stale.any { it.isAlive }) {
            Thread.sleep(100)
        }
        stale.filter { it.isAlive }.forEach { runCatching { it.destroyForcibly() } }
    }

    /** Свободный порт: сначала предпочтительный, иначе любой свободный. */
    private fun freePort(preferred: Int): Int {
        fun canBind(port: Int): Boolean = runCatching {
            java.net.ServerSocket(port, 8, java.net.InetAddress.getByName("127.0.0.1")).use { true }
        }.getOrDefault(false)
        if (canBind(preferred)) return preferred
        return runCatching {
            java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
        }.getOrDefault(preferred)
    }

    /** Системный компонент установлен и tun включён в настройках. */
    private fun usePrivileged(): Boolean =
        PrivilegedTunnel.isSupported() && PrivilegedTunnel.isInstalled() && Store.desktopTun()

    /**
     * Может ли наш собственный процесс поднять tun: на Windows он запускается
     * из run.bat с повышением прав, на macOS — только через `sudo` с
     * `WINGER_MODE=tun`. Иначе это прокси-режим.
     */
    private fun processTun(): Boolean = when {
        !Store.desktopTun() -> false
        System.getenv("WINGER_MODE").equals("proxy", ignoreCase = true) -> false
        else -> Platform.isWindows || System.getenv("WINGER_MODE").equals("tun", ignoreCase = true)
    }

    /**
     * Поднимает туннель через системный компонент.
     *
     * Локальная прослойка LTE4 остаётся в нашем процессе: ядро от root
     * подключается к 127.0.0.1:<порт> так же, как обычное приложение.
     */
    private suspend fun startPrivileged(xrayJson: String, gen: Int) {
        val shimPort = startShim()
        val config = coreConfig(xrayJson, shimPort, useTun = true)
        PrivilegedTunnel.configFile.writeText(config)
        PrivilegedTunnel.start()
        privilegedActive = true
        Store.running(true)
        Store.lastConfig(xrayJson)
        TunnelState.serviceAlive = true
        TunnelState.on()
        CoreTraffic.reset()
        startTrafficPolling(gen)

        // Демон не возвращает код: проверяем, что ядро реально слушает API.
        delay(2_500)
        if (gen != generation) return
        if (!clashAlive()) {
            VpnLog.add("системный туннель не ответил на Clash API")
            PrivilegedTunnel.stop()
            privilegedActive = false
            TunnelState.fail("системный туннель не поднялся, подробности в логе")
            Store.running(false)
            TunnelState.serviceAlive = false
        } else {
            VpnLog.add("системный туннель поднят: tun, весь трафик")
        }
    }

    /** Отвечает ли Clash API ядра (признак, что ядро живое). */
    private fun clashAlive(): Boolean = runCatching {
        (URL("http://127.0.0.1:9090/connections").openConnection() as HttpURLConnection).run {
            connectTimeout = 1_500
            readTimeout = 1_500
            responseCode in 200..299
        }
    }.getOrDefault(false)

    /**
     * Конфиг ядра: сначала полноценный тун (весь трафик системы), при отсутствии
     * прав — локальный прокси, чтобы приложение всё же работало.
     */
    internal fun coreConfig(xrayJson: String, shimPort: Int, useTun: Boolean): String {
        val options = TunnelOptions(
            dnsServer = Store.dnsServer(),
            directDomains = Store.bypass(),
            subRouting = if (Store.subRouting()) RoutingPlan.RULES_AND_DNS else RoutingPlan.OFF,
            useMux = Store.useMux(),
            mtu = if (Store.mtuLow()) 1280 else 1500,
            coreLogFile = Platform.file("singbox.log").absolutePath,
            cacheFile = Platform.file("cache.db").absolutePath,
            shimPort = shimPort,
            ipv6Proxy = Store.ipv6Proxy(),
        )
        val base = JSONObject(toSingBox(xrayJson, options))
        base.put("inbounds", org.json.JSONArray().put(inbound(useTun)))
        base.put(
            "experimental",
            JSONObject().put(
                "clash_api",
                JSONObject()
                    .put("external_controller", "127.0.0.1:$clashPort")
                    .put("default_mode", "rule"),
            ),
        )
        return base.toString()
    }

    /** tun — как на телефоне; mixed — запасной путь без прав администратора. */
    private fun inbound(useTun: Boolean): JSONObject = if (useTun) {
        JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", org.json.JSONArray().put("172.19.0.1/30"))
            .put("mtu", if (Store.mtuLow()) 1280 else 1500)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("stack", "gvisor")
            .put("sniff", true)
    } else {
        JSONObject()
            .put("type", "mixed")
            .put("tag", "mixed-in")
            .put("listen", "127.0.0.1")
            .put("listen_port", mixedPort)
    }

    /**
     * Ищет бинарь ядра: рядом с настройками, рядом с приложением (папка `core`
     * в portable-сборке), затем переменная окружения и `PATH`.
     */
    private fun resolveBinary(): File {
        val exe = if (Platform.isWindows) "sing-box.exe" else "sing-box"
        val cwd = File(System.getProperty("user.dir"))
        // jpackage-сборка (MSI) кладёт ресурсы сюда — см. appResourcesRootDir в build.gradle.kts.
        val packaged = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it, "core/$exe") }
        val candidates = buildList {
            add(corePath)
            packaged?.let { add(it) }
            add(File(cwd, "core/$exe"))
            add(File(cwd, exe))
            System.getenv("WINGER_CORE")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
            add(File(Platform.dataDir.parentFile ?: Platform.dataDir, exe))
        }
        candidates.firstOrNull { it.exists() && it.canExecute() }?.let { return it }
        // jpackage при копировании может потерять бит исполнения — возвращаем его.
        candidates.firstOrNull { it.exists() }?.let { file ->
            runCatching { file.setExecutable(true) }
            return file
        }
        val fromPath = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
            .map { File(it, exe) }
            .firstOrNull { it.exists() && it.canExecute() }
        return fromPath ?: error(
            "ядро sing-box не найдено. Положите ${corePath.name} в ${Platform.dataDir.absolutePath} " +
                "или в папку core рядом с приложением"
        )
    }

    /** Вывод ядра построчно уходит в журнал: без него не видно причин отказа. */
    private fun pipeOutput(process: Process, gen: Int) {
        scope.launch {
            runCatching {
                process.inputStream.bufferedReader().forEachLine { line ->
                    if (line.isNotBlank() && Diag.allow("core:$line", 400)) VpnLog.add("[core] $line")
                }
            }
        }
        scope.launch {
            while (gen == generation && process.isAlive) delay(500)
            if (gen != generation) return@launch
            val code = runCatching { process.exitValue() }.getOrNull() ?: return@launch
            VpnLog.add("ядро завершилось, код $code")
            TunnelState.serviceAlive = false
            Store.running(false)
            CoreTraffic.reset()
            TunnelState.fail("ядро остановилось (код $code)")
        }
    }

    /** Totals из Clash API — единственный честный источник байтов на десктопе. */
    private fun startTrafficPolling(gen: Int) {
        trafficJob?.cancel()
        trafficJob = scope.launch {
            while (gen == generation) {
                runCatching {
                    val (code, body) = get("http://127.0.0.1:$clashPort/connections")
                    if (code in 200..299) {
                        val json = JSONObject(body)
                        CoreTraffic.update(
                            json.optLong("downloadTotal"),
                            json.optLong("uploadTotal"),
                        )
                    }
                }
                delay(Store.trafficIntervalSec().coerceIn(1, 60) * 1000L)
            }
        }
    }

    private suspend fun get(url: String): Pair<Int, String> =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 1_500
                readTimeout = 2_500
                requestMethod = "GET"
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                code to (stream?.bufferedReader()?.readText().orEmpty())
            } finally {
                connection.disconnect()
            }
        }
}
