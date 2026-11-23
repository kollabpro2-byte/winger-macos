package pro.wingerapp.vpn

import java.io.File

/**
 * Диагностика для десктопа.
 *
 * Android-версия собирала `ApplicationExitInfo` и trace падений. Здесь аналог
 * проще: окружение (ОС, Java, память), хвост лога ядра и необработанные
 * исключения — этого достаточно, чтобы разобрать «ядро не поднялось».
 */
object Diag {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                VpnLog.add(
                    "необработанное исключение в ${thread.name}: " +
                        "${error.javaClass.simpleName}: ${error.message}\n" +
                        error.stackTraceToString().take(4_000)
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** На Android подбирало файл падения; здесь — просто отметка о запуске. */
    fun harvestCrash() {
        VpnLog.add(
            "запуск ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                "${Platform.osName} ${System.getProperty("os.arch")}, Java " +
                System.getProperty("java.version")
        )
    }

    /**
     * Дописывает хвост лога ядра в общий лог, чтобы он попал в «Скопировать».
     * Ядро пишет свой файл вне нашего контроля, и без этого его ошибки
     * (TLS, dial, DNS) не видны в интерфейсе.
     */
    fun harvestCoreLog(rotate: Boolean) {
        val core = Platform.file("singbox.log")
        if (!core.exists()) return
        runCatching {
            val text = core.readText().takeLast(6_000)
            if (text.isNotBlank()) VpnLog.add("--- лог ядра ---\n$text")
            if (rotate) core.writeText("")
        }
    }

    /** Снимок окружения вместо Android-списка причин выхода процесса. */
    fun logExitReasons() {
        val rt = Runtime.getRuntime()
        VpnLog.add(
            "окружение: ядер ${rt.availableProcessors()}, " +
                "память ${(rt.maxMemory() / 1024 / 1024)} МБ, " +
                "данные ${Platform.dataDir.absolutePath}"
        )
    }

    /** Не чаще одного раза в [intervalMs] — чтобы лог не забивался одинаковым. */
    private val lastAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun allow(key: String, intervalMs: Long): Boolean {
        val now = System.currentTimeMillis()
        val previous = lastAt[key] ?: 0L
        if (now - previous < intervalMs) return false
        lastAt[key] = now
        return true
    }

    internal fun tail(file: File, maxChars: Int): String =
        runCatching { file.readText().takeLast(maxChars) }.getOrDefault("")
}
