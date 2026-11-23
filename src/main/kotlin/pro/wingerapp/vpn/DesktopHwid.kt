package pro.wingerapp.vpn

import pro.wingerapp.vpn.net.Api

/**
 * Идентификатор устройства для панели (HWID).
 *
 * На Android это был ANDROID_ID, на десктопе — производная от имени компьютера,
 * пользователя и каталога данных. Значение стабильно между запусками, но не
 * содержит личных данных: в панель уходит только хэш.
 *
 * Установка на другом компьютере даст другой HWID — это ожидаемо: панель
 * считает такие установки отдельными устройствами, как и на телефонах.
 */
object DesktopHwid {
    private var cached: String? = null

    fun value(): String = cached ?: build().also { cached = it }

    private fun build(): String {
        val host = runCatching { java.net.InetAddress.getLocalHost().hostName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "unknown-host"
        val seed = listOf(
            host,
            System.getProperty("user.name").orEmpty(),
            Platform.osName,
            Platform.dataDir.absolutePath,
        ).joinToString("|")
        // 32 символа — та же длина, что у ANDROID_ID и резервного хэша на Android.
        return Api.sha256(seed).take(32)
    }
}
