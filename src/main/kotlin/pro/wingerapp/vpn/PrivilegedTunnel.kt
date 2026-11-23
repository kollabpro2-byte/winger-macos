package pro.wingerapp.vpn

import java.io.File
import java.security.MessageDigest

/**
 * Туннель на весь трафик без пароля при каждом включении.
 *
 * macOS: один раз ставится LaunchDaemon от root (запрос пароля администратора
 * ровно один раз). Демон следит за файлом состояния и поднимает/гасит `sing-box`
 * в tun-режиме. Приложение остаётся обычным пользователем, поэтому кнопка
 * «включить» дальше срабатывает мгновенно.
 *
 * Windows: один раз создаётся задача планировщика с «наивысшими правами»
 * (один UAC при установке). Дальше `schtasks /run|/end` — без UAC и мгновенно.
 *
 * Почему не Network Extension, как у Happ: он требует платного Apple Developer
 * и подписи с entitlements. Root-демон даёт тот же результат (utun доступен
 * root) и не требует сертификата.
 */
object PrivilegedTunnel {
    private val macPlist = File("/Library/LaunchDaemons/pro.wingerapp.vpn.tunnel.plist")
    private val macDir = File("/Library/Application Support/WingerVPN")
    private const val WIN_TASK = "WingerTunnel"

    val controlFile: File get() = File(Platform.dataDir, "tunnel.control")
    val configFile: File get() = File(Platform.dataDir, "core.json")

    fun isSupported(): Boolean = Platform.isMac || Platform.isWindows

        /**
     * Быстрая проверка: маркер пишется после успешной установки, а реальные
     * признаки проверяются только при его наличии. Опрос планировщика на каждом
     * кадре композиции был бы слишком дорогим.
     */
    private val marker: File get() = File(Platform.dataDir, "privileged.installed")

    fun isInstalled(): Boolean {
        if (!marker.exists()) return false
        return when {
            Platform.isMac -> macPlist.exists() && File(macDir, "winger-tunnel.sh").exists()
            Platform.isWindows -> true
            else -> false
        }
    }

    /** Ставит компонент. Возвращает текст ошибки для UI или null при успехе. */
    fun install(corePath: File): String? = when {
        Platform.isMac -> installMac(corePath)
        Platform.isWindows -> installWindows(corePath)
        else -> "системный режим поддержан только на macOS и Windows"
    }

    fun uninstall(): String? {
        marker.delete()
        return when {
            Platform.isMac -> runAdmin(resourceScript("uninstall-mac.sh").trim()).errorOrNull()
            Platform.isWindows -> {
                val cmd = "schtasks /delete /tn $WIN_TASK /f >nul 2>&1 & sc stop WingerTunnel >nul 2>&1 & " +
                    "sc delete WingerTunnel >nul 2>&1"
                runElevated(cmd).errorOrNull()
            }
            else -> null
        }
    }

    // --- управление (без пароля) ---------------------------------------------------

    fun start() {
        controlFile.writeText("on")
        if (Platform.isWindows) runCatching {
            ProcessBuilder("schtasks", "/run", "/tn", WIN_TASK)
                .redirectErrorStream(true).start().waitFor()
        }
        VpnLog.add("системный туннель: старт (${if (Platform.isMac) "launchd" else "планировщик"})")
    }

    fun stop() {
        controlFile.writeText("off")
        if (Platform.isWindows) runCatching {
            ProcessBuilder("schtasks", "/end", "/tn", WIN_TASK)
                .redirectErrorStream(true).start().waitFor()
        }
        VpnLog.add("системный туннель: стоп")
    }

    // --- внутреннее ---------------------------------------------------------------

    private fun resourceScript(name: String): String =
        PrivilegedTunnel::class.java.classLoader!!
            .getResourceAsStream("installer/$name")!!
            .use { it.readBytes().toString(Charsets.UTF_8) }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // --- macOS ---------------------------------------------------------------------

    private fun installMac(corePath: File): String? {
        if (!corePath.exists()) return "ядро не найдено: ${corePath.absolutePath}"
        val script = File.createTempFile("winger-install-", ".sh")
        script.writeText(
            resourceScript("install-mac.sh")
                .replace("@DATA@", Platform.dataDir.absolutePath)
                .replace("@CORE@", corePath.absolutePath),
        )
        script.setReadable(false, false)
        script.setReadable(true, true)
        script.setExecutable(true, true)

        // Хеш закрывает окно подмены файла между записью и запуском от root:
        // команда сначала сверяет SHA-256 и только потом исполняет.
        val sum = sha256(script.readBytes())
        val command = "H=$(shasum -a 256 '${script.absolutePath}' | cut -d' ' -f1); " +
            "[ \"\$H\" = \"$sum\" ] && /bin/sh '${script.absolutePath}' || echo 'hash mismatch'"
        val result = runAdmin(command)
        script.delete()
        val error = result.errorOrNull()
        if (error == null) marker.writeText("1")
        return error
    }

    private fun runAdmin(command: String): ExecResult {
        val escaped = command.replace("\\", "\\\\").replace("\"", "\\\"")
        val process = ProcessBuilder(
            "/usr/bin/osascript", "-e",
            "do shell script \"$escaped\" with administrator privileges",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return ExecResult(process.waitFor(), output)
    }

    // --- Windows -------------------------------------------------------------------

    private fun installWindows(corePath: File): String? {
        if (!corePath.exists()) return "ядро не найдено: ${corePath.absolutePath}"
        // Задача запускает ядро от имени пользователя, но с наивысшими правами:
        // UAC спрашивается только при создании задачи, не при каждом включении.
        val run = "\"${corePath.absolutePath}\" run -c \"${configFile.absolutePath}\""
        val create = "schtasks /create /tn $WIN_TASK /tr \"$run\" /sc once /st 00:00 /rl HIGHEST /f"
        val error = runElevated(create).errorOrNull()
        if (error == null) marker.writeText("1")
        return error
    }

    /** Запускает команду через UAC (PowerShell Start-Process -Verb RunAs). */
    private fun runElevated(command: String): ExecResult {
        val escaped = command.replace("'", "''")
        val process = ProcessBuilder(
            "powershell", "-NoProfile", "-Command",
            "Start-Process -FilePath cmd.exe -ArgumentList '/c $escaped' -Verb RunAs -Wait",
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return ExecResult(process.waitFor(), output)
    }

    private data class ExecResult(val code: Int, val output: String) {
        fun errorOrNull(): String? = when {
            code == 0 -> null
            output.contains("User canceled", ignoreCase = true) -> "установка отменена"
            output.contains("hash mismatch") -> "проверка целостности не прошла"
            else -> output.trim().takeLast(300).ifBlank { "код $code" }
        }
    }
}
