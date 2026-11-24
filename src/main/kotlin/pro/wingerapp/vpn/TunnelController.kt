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

    /**
     * Имя сервера → тег outbound'а в текущем конфиге. По этой карте UI просит
     * у ядра задержку каждого сервера (Clash API), как это делают Happ/Incy.
     */
    @Volatile
    private var pingTags: Map<String, String> = emptyMap()

    /** Сколько серверов сейчас имеют outbound для замера (0 — конфиг без них). */
    fun pingTagCount(): Int = pingTags.size

    /** Тег outbound'а для замера пинга конкретного сервера. */
    fun pingTag(serverName: String): String? = pingTags[serverName]

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
    fun start(xrayJson: String, rawSubscription: String = "", allowInstall: Boolean = true) {
        val gen = ++generation
        TunnelState.clearError()
        scope.launch {
            try {
                stopInternal(notify = false)

                // Установлен системный компонент (root-демон/задача планировщика):
                // tun поднимает он, а приложение только передаёт конфиг. Тогда
                // Cmd+Q и перезапуск приложения туннель не рвут.
                //
                // Если компонента ещё нет — ставим его прямо сейчас: один запрос
                // пароля при первом включении, как у Happ/Incy. Отказ не мешает:
                // ниже сработает обычный прокси-режим.
                if (allowInstall && !usePrivileged() && Store.desktopTun() && PrivilegedTunnel.isSupported() &&
                    !PrivilegedTunnel.isInstalled()
                ) {
                    VpnLog.add("первый запуск: установка системного режима")
                    val error = kotlinx.coroutines.withContext(Dispatchers.IO) {
                        runCatching { PrivilegedTunnel.install(coreBinary()) }.getOrElse { it.message }
                    }
                    if (error != null) {
                        VpnLog.add("системный режим не установлен: $error")
                    }
                }

                if (usePrivileged()) {
                    startPrivileged(xrayJson, rawSubscription, gen)
                    return@launch
                }

                // Иначе — свой процесс. Ядро могло остаться от прошлого запуска
                // (Cmd+Q, падение) и держать порты; иначе будет bind: in use.
                killStaleCore()
                mixedPort = freePort(2080)
                clashPort = freePort(9090)

                val shimPort = startShim()
                val tun = processTun()
                val config = coreConfig(xrayJson, shimPort, tun, raw = rawSubscription)
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
                if (rawSubscription.isNotBlank()) Store.lastRaw(rawSubscription)
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

        // Системный режим ещё не установлен: не поднимаем прокси молча — первое
        // нажатие орла должно показать установку (один пароль) и включить tun.
        if (!PrivilegedTunnel.isInstalled() && Store.desktopTun() && PrivilegedTunnel.isSupported()) {
            Store.running(false)
            TunnelState.off()
            VpnLog.add("туннель не восстановлен: системный режим не установлен")
            return
        }

        // Восстановление не должно внезапно спрашивать пароль: системный режим
        // ставится только по явному нажатию орла. Подписку передаём целиком —
        // без неё в конфиге не будет outbound'ов для пинга остальных серверов.
        start(config, Store.lastRaw(), allowInstall = false)
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
    private suspend fun startPrivileged(xrayJson: String, raw: String, gen: Int) {
        // Права на файл состояния могли остаться от старой версии установщика
        // (файл создан root'ом). Тогда переустанавливаем компонент — один пароль.
        if (!PrivilegedTunnel.canControl()) {
            VpnLog.add("нет прав на файл состояния — повторная установка системного режима")
            val error = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { PrivilegedTunnel.install(coreBinary()) }.getOrElse { it.message }
            }
            if (error != null || !PrivilegedTunnel.canControl()) {
                TunnelState.fail(error ?: "нет прав на файл состояния системного режима")
                return
            }
        }

        val shimPort = startShim()
        val config = coreConfig(
            xrayJson, shimPort, useTun = true, privileged = true,
            raw = raw.ifBlank { Store.lastRaw() },
        )
        if (raw.isNotBlank()) Store.lastRaw(raw)
        PrivilegedTunnel.configFile.writeText(config)
        // Гасим и поднимаем заново: демон держит уже запущенное ядро и сам
        // конфиг не перечитывает, иначе остался бы старый сервер.
        PrivilegedTunnel.stop()
        delay(700)
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
    /** Отвечает ли Clash API ядра: до его готовности замеры бессмысленны. */
    fun coreApiAlive(): Boolean = clashAlive()

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
    internal fun coreConfig(
        xrayJson: String,
        shimPort: Int,
        useTun: Boolean,
        privileged: Boolean = false,
        raw: String = "",
    ): String {
        val options = TunnelOptions(
            dnsServer = Store.dnsServer(),
            directDomains = Store.bypass(),
            subRouting = if (Store.subRouting()) RoutingPlan.RULES_AND_DNS else RoutingPlan.OFF,
            useMux = Store.useMux(),
            mtu = if (Store.mtuLow()) 1280 else 1500,
            coreLogFile = coreLogPath(privileged),
            cacheFile = Platform.file("cache.db").absolutePath,
            shimPort = shimPort,
            ipv6Proxy = Store.ipv6Proxy(),
        )
        val base = JSONObject(toSingBox(xrayJson, options))
        base.put("inbounds", org.json.JSONArray().put(inbound(useTun)))

        // По одному outbound'у на каждый сервер — только ради замеров через
        // Clash API. Выбранный сервер уже идёт под тегом proxy.
        val selectedName = runCatching { JSONObject(xrayJson).optString("remarks") }.getOrDefault("")
        if (raw.isNotBlank()) {
            val (extra, tags) = measurementOutbounds(raw, selectedName, options)
            val outbounds = base.getJSONArray("outbounds")
            for (i in 0 until extra.length()) outbounds.put(extra.getJSONObject(i))
            pingTags = if (selectedName.isBlank()) tags else tags + (selectedName to "proxy")
            VpnLog.add("пинг: outbound'ов замера ${extra.length()}")
        } else {
            pingTags = emptyMap()
        }

        // Шлюзы прослойки LTE4/LTE5 — напрямую, иначе на macOS её собственные
        // запросы уходят в туннель и замыкаются на этот же прокси.
        val gateways = if (raw.isBlank()) emptyList() else shimHosts(raw)
        if (gateways.isNotEmpty()) {
            val route = base.getJSONObject("route")
            val old = route.getJSONArray("rules")
            // Важно: правило должно идти ПОСЛЕ sniff/hijack-dns. Если поставить
            // его первым, домен ещё не определён (трафик приходит на IP) и
            // совпадения нет — запрос снова ушёл бы в прокси.
            val hostRule = JSONObject()
                .put("action", "route")
                .put("outbound", "direct")
                .put("domain", org.json.JSONArray(gateways))
            val rules = org.json.JSONArray()
            var inserted = false
            for (i in 0 until old.length()) {
                val rule = old.getJSONObject(i)
                val routes = rule.has("outbound") || rule.has("rule_set") || rule.has("ip_cidr")
                if (!inserted && routes) {
                    rules.put(hostRule)
                    inserted = true
                }
                rules.put(rule)
            }
            if (!inserted) rules.put(hostRule)
            route.put("rules", rules)
            VpnLog.add("шлюзы прослойки напрямую: ${gateways.size}")
        }
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
    /**
     * Куда ядро пишет свой журнал. В системном режиме на macOS ядро работает от
     * root, поэтому писать в пользовательский каталог ему нельзя — иначе файл
     * создаётся root'ом и приложение не может его подрезать.
     */
    private fun coreLogPath(privileged: Boolean): String =
        if (privileged && Platform.isMac) "/Library/Application Support/WingerVPN/singbox.log"
        else Platform.file("singbox.log").absolutePath

    private fun inbound(useTun: Boolean): JSONObject = if (useTun) {
        JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", org.json.JSONArray().put("172.19.0.1/30"))
            .put("mtu", if (Store.mtuLow()) 1280 else 1500)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("stack", "gvisor")
        // `sniff` здесь больше не пишем: с sing-box 1.13 это legacy-поле инбаунда,
        // ядро падает с FATAL. Определение протокола делает правило маршрута
        // {"action":"sniff"} — оно уже есть в route.rules.
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
