package pro.wingerapp.vpn

import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Everything that survives a restart.
 *
 * Android-версия опиралась на `SharedPreferences` и Keystore. На десктопе два
 * файла в [Platform.dataDir]:
 *
 *  * `settings.json` — обычные настройки, читаются мгновенно;
 *  * `secrets.bin` — сессия, логин/пароль и последний конфиг туннеля, зашифрованы
 *    AES-GCM ключом из `key.bin` (права 0600).
 *
 * Ключ лежит рядом с данными, поэтому это защита от «заглянуть в файл», а не от
 * программы, запущенной от имени того же пользователя. Для Windows следующий шаг
 * — обернуть ключ в DPAPI (`CryptProtectData`), тогда его нельзя будет унести на
 * другую машину; интерфейс `Store` при этом не меняется.
 */
object Store {
    private val settingsFile = Platform.file("settings.json")
    private val secretsFile = Platform.file("secrets.bin")
    private val keyFile = Platform.file("key.bin")

    private val lock = Any()
    private var settingsCache: MutableMap<String, String>? = null
    private var secretsCache: JSONObject? = null

    // --- низкий уровень ---------------------------------------------------------

    private fun loadSettings(): MutableMap<String, String> = synchronized(lock) {
        settingsCache?.let { return it }
        val map = mutableMapOf<String, String>()
        runCatching {
            if (settingsFile.exists()) {
                val json = JSONObject(settingsFile.readText())
                json.keys().forEach { key -> map[key] = json.optString(key) }
            }
        }
        settingsCache = map
        map
    }

    private fun persistSettings() {
        synchronized(lock) {
        val map = settingsCache ?: return
        runCatching {
            val json = JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            atomicWrite(settingsFile, json.toString().toByteArray())
        }
        }
    }

    private fun secretKey(): SecretKeySpec {
        if (!keyFile.exists()) {
            val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
            atomicWrite(keyFile, raw)
            keyFile.setReadable(false, false)
            keyFile.setReadable(true, true)
            keyFile.setWritable(false, false)
            keyFile.setWritable(true, true)
        }
        return SecretKeySpec(keyFile.readBytes(), "AES")
    }

    private fun loadSecrets(): JSONObject = synchronized(lock) {
        secretsCache?.let { return it }
        val obj = runCatching {
            if (!secretsFile.exists()) JSONObject() else {
                val packed = secretsFile.readBytes()
                val iv = packed.copyOfRange(0, 12)
                val body = packed.copyOfRange(12, packed.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
                JSONObject(String(cipher.doFinal(body), Charsets.UTF_8))
            }
        }.getOrElse {
            // Ключ потерян или файл повреждён: начинаем с чистого листа, но не
            // роняем приложение — пользователь просто войдёт заново.
            VpnLog.add("секреты недоступны (${it.javaClass.simpleName}), нужен повторный вход")
            JSONObject()
        }
        secretsCache = obj
        obj
    }

    private fun persistSecrets() {
        synchronized(lock) {
        val obj = secretsCache ?: return
        runCatching {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            val body = cipher.doFinal(obj.toString().toByteArray())
            atomicWrite(secretsFile, iv + body)
        }
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun get(key: String, fallback: String = ""): String =
        loadSettings()[key] ?: fallback

    private fun put(key: String, value: String) {
        loadSettings()[key] = value
        persistSettings()
    }

    private fun getBool(key: String, fallback: Boolean = false) =
        get(key).takeIf { it.isNotEmpty() }?.toBooleanStrictOrNull() ?: fallback

    private fun getInt(key: String, fallback: Int) = get(key).toIntOrNull() ?: fallback

    private fun secretGet(key: String): String = loadSecrets().optString(key)

    private fun secretPut(key: String, value: String) {
        loadSecrets().put(key, value)
        persistSecrets()
    }

    private fun secretRemove(key: String) {
        loadSecrets().remove(key)
        persistSecrets()
    }

    /** На Android переносило настройки из зашифрованного файла; здесь нечего переносить. */
    fun migrateSettingsIntoPlain() = Unit

    // --- credentials -------------------------------------------------------------

    fun save(login: String, password: String) {
        secretPut("login", login)
        secretPut("password", password)
    }

    fun login(): String = secretGet("login")
    fun password(): String = secretGet("password")
    fun cookies(): String = secretGet("cookies")

    fun cookies(raw: String) = secretPut("cookies", raw)

    /** Drop the session, keep the settings. */
    fun logout() {
        secretRemove("login")
        secretRemove("password")
        secretRemove("cookies")
        secretRemove("lastConfig")
        put("running", "false")
    }

    // --- tunnel ------------------------------------------------------------------

    fun running(): Boolean = getBool("running", false)
    fun running(on: Boolean) = put("running", on.toString())

    fun lastConfig(): String = secretGet("lastConfig")
    fun lastConfig(json: String) = secretPut("lastConfig", json)
    fun clearLastConfig() = secretRemove("lastConfig")

    fun subCache(): String = secretGet("subCache")
    fun subCache(json: String) = secretPut("subCache", json)
    fun clearSubCache() = secretRemove("subCache")

    // --- settings ----------------------------------------------------------------

    fun dnsMode(): String = get("dnsMode", "auto")
    fun dnsMode(mode: String) = put("dnsMode", mode)
    fun dnsCustom(): String = get("dnsCustom")
    fun dnsCustom(ip: String) = put("dnsCustom", ip)

    fun dnsServer(): String? = when (dnsMode()) {
        "cf" -> "1.1.1.1"
        "gg" -> "8.8.8.8"
        "own" -> dnsCustom().trim().takeIf { it.isNotBlank() }
        else -> null
    }

    fun bypass(): List<String> = get("bypass").lines().map { it.trim() }.filter { it.isNotEmpty() }
    fun bypass(hosts: List<String>) = put("bypass", hosts.joinToString("\n"))

    fun useMux(): Boolean = getBool("useMux", false)
    fun useMux(on: Boolean) = put("useMux", on.toString())

    fun mtuLow(): Boolean = getBool("mtuLow", false)
    fun mtuLow(on: Boolean) = put("mtuLow", on.toString())

    /** Только IPv4 для адреса прокси: IPv6 первой отвечает реже, но ломает dial. */
    fun ipv6Proxy(): Boolean = getBool("ipv6Proxy", false)
    fun ipv6Proxy(on: Boolean) = put("ipv6Proxy", on.toString())

    fun trafficIntervalSec(): Int = getInt("trafficIntervalSec", 10).coerceIn(1, 60)
    fun trafficIntervalSec(seconds: Int) =
        put("trafficIntervalSec", seconds.coerceIn(1, 60).toString())

    /** Кадровый режим включён всегда; если шлюз его не поддержит — откат на `/ws`. */
    fun lte4Mode(): Int = Lte4.MODE_FRAMED

    fun lte4Streams(): Int = lte4StreamsDefault()
    fun lte4Streams(n: Int) = put("lte4Streams", n.coerceIn(1, 32).toString())

    /**
     * Сколько потоков просить у релея. На Android потолок зависел от класса
     * памяти устройства; на десктопе ОЗУ достаточно, поэтому всегда 32.
     */
    fun lte4StreamsDefault(): Int = 32

    /** Маршрутизация и DNS из подписки включены всегда. */
    fun subRouting(): Boolean = true

    /**
     * Туннель на весь трафик системы (tun) или локальный прокси.
     *
     * На Windows tun — режим по умолчанию: он требует прав администратора и
     * `wintun.dll`, но только так VPN работает для всех программ. На macOS по
     * умолчанию прокси: tun там требует root, поэтому включается осознанно.
     *
     * `WINGER_MODE=proxy` — принудительно прокси (пакет без прав), `WINGER_MODE=tun`
     * — принудительно tun (запуск через `sudo`, см. run-tun.command).
     */
    fun desktopTun(): Boolean {
        when (System.getenv("WINGER_MODE")?.lowercase()) {
            "proxy" -> return false
            "tun" -> return true
        }
        // Установленный системный компонент умеет tun сам — он и есть основной путь.
        if (PrivilegedTunnel.isInstalled()) return true
        // На десктопе tun — цель по умолчанию: если системный режим ещё не
        // поставлен, приложение предложит его при первом включении. Сохранённое
        // значение не учитываем: отдельного переключателя в интерфейсе нет, а
        // старое false (из отладочных прогонов) блокировало установку.
        return Platform.isWindows || Platform.isMac
    }

    fun desktopTun(on: Boolean) = put("desktopTun", on.toString())

    fun coreLog(): Boolean = getBool("coreLog", false)
    fun coreLog(on: Boolean) = put("coreLog", on.toString())

    /** 0 — тёмная, 1 — средняя, 2 — светлая. По умолчанию средняя. */
    fun themeMode(): Int = getInt("themeMode", 1).coerceIn(0, 2)
    fun themeMode(mode: Int) = put("themeMode", mode.coerceIn(0, 2).toString())

    fun logClearMinutes(): Int = getInt("logClearMinutes", 30).coerceIn(5, 120)
    fun logClearMinutes(minutes: Int) =
        put("logClearMinutes", minutes.coerceIn(5, 120).toString())

    /** `null` — пользователь ещё ничего не раскрывал; иначе — его выбор. */
    fun openSections(): Set<String>? {
        val raw = loadSettings()["openSections"] ?: return null
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun openSections(ids: Set<String>) = put("openSections", ids.joinToString(","))

    fun devicesOpen(): Boolean = getBool("devicesOpen", true)
    fun devicesOpen(on: Boolean) = put("devicesOpen", on.toString())
}
