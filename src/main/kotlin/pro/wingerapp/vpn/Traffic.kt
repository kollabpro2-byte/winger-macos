package pro.wingerapp.vpn

/**
 * Байты туннеля для строки «↑ / ↓».
 *
 * На Android счётчики брались из `TrafficStats` по UID процесса, потому что ядро
 * работало внутри приложения. На десктопе ядро — отдельный процесс, поэтому
 * totals отдаёт сам sing-box через Clash API (`/connections`: `downloadTotal`,
 * `uploadTotal`). Значения кладёт сюда [TunnelController], UI их только читает.
 */
object CoreTraffic {
    @Volatile
    var rx: Long = 0L
        private set

    @Volatile
    var tx: Long = 0L
        private set

    /** Есть ли уже хотя бы один ответ API: до него счётчики неизвестны. */
    @Volatile
    var known: Boolean = false
        private set

    fun update(download: Long, upload: Long) {
        rx = download.coerceAtLeast(0)
        tx = upload.coerceAtLeast(0)
        known = true
    }

    fun reset() {
        rx = 0
        tx = 0
        known = false
    }
}

/** `first` = получено (rx), `second` = отдано (tx) — как и в Android-версии. */
fun tunTraffic(): Pair<Long, Long>? =
    if (CoreTraffic.known) CoreTraffic.rx to CoreTraffic.tx else null

fun fmtBytes(n: Long): String {
    val v = n.coerceAtLeast(0)
    return when {
        v < 1024 -> "$v Б"
        v < 1024 * 1024 -> "%.1f КБ".format(v / 1024.0).replace('.', ',')
        v < 1024L * 1024 * 1024 -> "%.1f МБ".format(v / 1024.0 / 1024).replace('.', ',')
        else -> "%.2f ГБ".format(v / 1024.0 / 1024 / 1024).replace('.', ',')
    }
}
