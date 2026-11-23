#!/usr/bin/env python3
"""TCP-пинг в обход туннеля: сокет привязывается к физическому интерфейсу."""
import io

VPN = "src/main/kotlin/pro/wingerapp/vpn/"


def patch(path, pairs):
    text = io.open(path, encoding="utf-8").read()
    for old, new, label in pairs:
        if old not in text:
            raise SystemExit(f"{path}: не найдено — {label}")
        text = text.replace(old, new, 1)
    io.open(path, "w", encoding="utf-8").write(text)
    print(path.split("/")[-1], "ok")


# 1. JNA + --add-opens: нужен доступ к дескриптору сокета и setsockopt.
patch("build.gradle.kts", [
    ('    implementation("org.json:json:20240303")',
     '    implementation("org.json:json:20240303")\n'
     '    // TCP-пинг в обход tun: setsockopt(IP_BOUND_IF) через JNA.\n'
     '    implementation("net.java.dev.jna:jna:5.14.0")',
     "зависимость JNA"),
    ('        mainClass = "pro.wingerapp.vpn.MainKt"',
     '        mainClass = "pro.wingerapp.vpn.MainKt"\n'
     '        // Доступ к дескриптору сокета для привязки к интерфейсу.\n'
     '        jvmArgs += listOf(\n'
     '            "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",\n'
     '            "--add-opens=java.base/java.io=ALL-UNNAMED",\n'
     '        )',
     "jvmArgs"),
])

# 2. Замер TCP мимо туннеля.
patch(VPN + "Ping.kt", [(
    "private const val CONNECT_TIMEOUT_MS = 1200",
    '''/**
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

    fun ping(host: String, port: Int, timeoutMs: Int = 1500): Int? {
        if (!supported || host.isBlank() || port !in 1..65535) return null
        val ips = resolveCached(host)
        for (ip in ips.take(2)) {
            val ms = runCatching {
                java.nio.channels.SocketChannel.open().use { channel ->
                    bind(channel, ifIndex)
                    val start = System.nanoTime()
                    channel.socket().connect(InetSocketAddress(ip, port), timeoutMs)
                    ((System.nanoTime() - start) / 1_000_000).toInt().coerceAtLeast(1)
                }
            }.getOrNull() ?: continue
            return ms
        }
        return null
    }

    private fun bind(channel: java.nio.channels.SocketChannel, index: Int) {
        val fdField = channel.javaClass.getDeclaredField("fd").apply { isAccessible = true }
        val descriptor = fdField.get(channel) as java.io.FileDescriptor
        val fd = java.io.FileDescriptor::class.java
            .getDeclaredField("fd").apply { isAccessible = true }.getInt(descriptor)
        val rc = libc!!.setsockopt(fd, IPPROTO_IP, IP_BOUND_IF, intArrayOf(index), 4)
        check(rc == 0) { "setsockopt(IP_BOUND_IF) вернул $rc" }
    }
}

private const val CONNECT_TIMEOUT_MS = 1200''',
    "InterfacePing",
)])

print("часть 1 готова")
