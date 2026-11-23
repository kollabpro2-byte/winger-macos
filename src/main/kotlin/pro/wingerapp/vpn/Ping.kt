package pro.wingerapp.vpn

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * TCP-пинг: время TCP-рукопожатия с конечным адресом сервера.
 *
 * Честность: измеряется только `connect`, DNS в замер не входит, ICMP не
 * используется (на мобильных сетях он часто зарезан). Скорость: адреса
 * кэшируются на 10 минут — иначе на подписке из 50 серверов каждый замер делает
 * 50 DNS-запросов; из двух попыток берётся лучшая.
 *
 * Мусорные адреса отбрасываются: `0.0.0.0`, `127.0.0.1`, link-local и multicast
 * принимают TCP мгновенно (0–1 мс), и именно из-за них «все пинги стали 1 мс».
 */
fun tcpPing(host: String, port: Int, attempts: Int = 2): Int? {
    if (host.isBlank() || port !in 1..65535) return null
    val ips = resolveCached(host)
    if (ips.isEmpty()) return null
    var best: Int? = null
    // До двух адресов одного имени: если один недоступен, второй может работать.
    for (ip in ips.take(2)) {
        repeat(attempts.coerceIn(1, 4)) {
            val ms = runCatching {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    val start = System.nanoTime()
                    socket.connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                    ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
                }
            }.getOrNull() ?: return@repeat
            if (best == null || ms < best!!) best = ms
            // Первая же быстрая попытка — дальше незачем, пользователь ждёт ответа.
            if (best!! <= FAST_ENOUGH_MS) return best
        }
        if (best != null) return best
    }
    return best
}

/**
 * Задержка через ядро: Clash API `/proxies/{tag}/delay`.
 *
 * Зачем: при включённом tun TCP-рукопожатие завершает локальный стек ядра
 * (gvisor), поэтому прямой `tcpPing` из приложения даёт ~1 мс у любого сервера —
 * это честное время до ядра, а не до сервера. Здесь запрос реально проходит
 * через выбранный outbound до `generate_204`, и число отражает путь до сервера.
 */
fun coreDelay(
    proxyTag: String = "proxy",
    timeoutMs: Int = 3000,
    url: String = "http://www.gstatic.com/generate_204",
): Int? = runCatching {
    val encoded = java.net.URLEncoder.encode(url, "UTF-8")
    val connection = (
        java.net.URL("http://127.0.0.1:9090/proxies/$proxyTag/delay?timeout=$timeoutMs&url=$encoded")
            .openConnection() as java.net.HttpURLConnection
        )
    connection.connectTimeout = 1_500
    connection.readTimeout = timeoutMs + 1_500
    connection.requestMethod = "GET"
    if (connection.responseCode !in 200..299) return null
    val body = connection.inputStream.bufferedReader().readText()
    org.json.JSONObject(body).optInt("delay", -1).takeIf { it > 0 }
}.getOrNull()

/**
 * TCP-пинг в обход туннеля.
 *
 * Пока tun включён, обычный `connect()` завершает локальный стек ядра, поэтому
 * числа были бы ~1 мс. Привязываем сокет к физическому интерфейсу
 * (`setsockopt(IP_BOUND_IF)`) — тогда пакет уходит мимо tun и мы меряем реальное
 * рукопожатие до сервера, как это делает Incy. Недоступный сервер даёт null —
 * в списке это «n/a», то есть доступность проверяется честно.
 *
 * Только macOS: на Windows/Linux функция вернёт null, и вызывающий код
 * переключится на замер через ядро.
 */
object InterfacePing {
    private const val IPPROTO_IP = 0
    private const val IP_BOUND_IF = 25
    private const val IPPROTO_IPV6 = 41
    private const val IPV6_BOUND_IF = 125

    private interface LibC : com.sun.jna.Library {
        fun if_nametoindex(name: String): Int
        fun setsockopt(fd: Int, level: Int, optname: Int, optval: IntArray, optlen: Int): Int
    }

    private val libc: LibC? = runCatching {
        if (System.getProperty("os.name").orEmpty().startsWith("Mac")) {
            com.sun.jna.Native.load("c", LibC::class.java)
        } else null
    }.getOrNull()

    /** Индекс интерфейса, через который идёт обычный трафик (Wi-Fi/Ethernet). */
    private val ifIndex: Int by lazy {
        val candidates = listOf("en0", "en1", "en2")
        candidates.firstNotNullOfOrNull { name ->
            runCatching { libc?.if_nametoindex(name) }.getOrNull()?.takeIf { it > 0 }
        } ?: 0
    }

    val supported: Boolean get() = libc != null && ifIndex > 0

    /** Последняя ошибка привязки/подключения — для диагностики в логе. */
    @Volatile
    var lastError: String? = null
        private set

    fun ping(host: String, port: Int, timeoutMs: Int = 1500): Int? {
        if (!supported || host.isBlank() || port !in 1..65535) return null
        val ips = resolveCached(host)
        for (ip in ips.take(2)) {
            val ms = runCatching {
                // Семейство сокета должно совпадать с адресом: IPv4-опция на
                // IPv6-сокете даёт EINVAL.
                val family = if (ip is Inet4Address) {
                    java.net.StandardProtocolFamily.INET
                } else {
                    java.net.StandardProtocolFamily.INET6
                }
                java.nio.channels.SocketChannel.open(family).use { channel ->
                    bind(channel, ifIndex, ip is Inet4Address)
                    val start = System.nanoTime()
                    channel.socket().connect(InetSocketAddress(ip, port), timeoutMs)
                    ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
                }
            }.onFailure { lastError = "${it.javaClass.simpleName}: ${it.message}" }.getOrNull() ?: continue
            return ms
        }
        return null
    }

    private fun bind(channel: java.nio.channels.SocketChannel, index: Int, ipv4: Boolean) {
        val fdField = channel.javaClass.getDeclaredField("fd").apply { isAccessible = true }
        val descriptor = fdField.get(channel) as java.io.FileDescriptor
        val fd = java.io.FileDescriptor::class.java
            .getDeclaredField("fd").apply { isAccessible = true }.getInt(descriptor)
        val level = if (ipv4) IPPROTO_IP else IPPROTO_IPV6
        val option = if (ipv4) IP_BOUND_IF else IPV6_BOUND_IF
        val rc = libc!!.setsockopt(fd, level, option, intArrayOf(index), 4)
        check(rc == 0) { "setsockopt rc=$rc errno=" + com.sun.jna.Native.getLastError() + " fd=" + fd + " if=" + index }
    }
}

private const val CONNECT_TIMEOUT_MS = 1200
private const val IP_TTL_MS = 10L * 60L * 1000L
private const val FAST_ENOUGH_MS = 120

private data class CachedIps(val at: Long, val ips: List<InetAddress>)

private val ipCache = HashMap<String, CachedIps>()
private val ipLock = Any()

/**
 * Годится ли адрес для замера. Служебные адреса отвечают на TCP мгновенно,
 * не выходя в сеть, и дают «1 мс» у любого сервера.
 */
internal fun isUsableAddress(addr: InetAddress): Boolean = !(
    addr.isAnyLocalAddress || addr.isLoopbackAddress ||
        addr.isLinkLocalAddress || addr.isMulticastAddress
    )

/**
 * Адрес, полученный по имени, не должен быть локальным: так выглядит подменённый
 * или заблокированный DNS ( captive portal, «домен заблокирован» ), и пинг до
 * такого адреса опять даст «1 мс» у нерабочего сервера.
 */
internal fun isSaneResolvedAddress(addr: InetAddress): Boolean =
    isUsableAddress(addr) && !addr.isSiteLocalAddress

/** Резолв с кэшем: IPv4 в приоритете, AAAA на мобильных сетях часто не ходит. */
internal fun resolveCached(host: String): List<InetAddress> {
    val now = System.currentTimeMillis()
    synchronized(ipLock) {
        val hit = ipCache[host]
        if (hit != null && now - hit.at < IP_TTL_MS) return hit.ips
    }
    val ips = pickIps(host)
    synchronized(ipLock) { ipCache[host] = CachedIps(now, ips) }
    return ips
}

private fun pickIps(host: String): List<InetAddress> {
    // Литерал адреса резолвить незачем, но проверить на служебность надо.
    val literal = runCatching { InetAddress.getByName(host) }.getOrNull()
    if (literal != null && host.count { c -> c == '.' } == 3) {
        return if (isUsableAddress(literal)) listOf(literal) else emptyList()
    }
    val all = runCatching { InetAddress.getAllByName(host).toList() }.getOrNull() ?: return emptyList()
    val usable = all.filter { isSaneResolvedAddress(it) }
    val v4 = usable.filterIsInstance<Inet4Address>()
    return (if (v4.isNotEmpty()) v4 else usable).distinct()
}

/** Сбросить кэш адресов — при смене сети, чтобы не держать старые IP. */
fun clearPingCaches() {
    synchronized(ipLock) { ipCache.clear() }
}



