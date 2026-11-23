#!/usr/bin/env python3
"""TunnelController: outbound'ы замера, карта тегов, передача подписки в конфиг."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/TunnelController.kt"
text = io.open(PATH, encoding="utf-8").read()


def sub(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)


# 1. Карта «имя сервера → тег outbound'а» для замеров.
sub(
    "    /** Текущая сессия поднята системным демоном, а не нашим процессом. */\n"
    "    @Volatile\n"
    "    private var privilegedActive = false",
    "    /** Текущая сессия поднята системным демоном, а не нашим процессом. */\n"
    "    @Volatile\n"
    "    private var privilegedActive = false\n\n"
    "    /**\n"
    "     * Имя сервера → тег outbound'а в текущем конфиге. По этой карте UI просит\n"
    "     * у ядра задержку каждого сервера (Clash API), как это делают Happ/Incy.\n"
    "     */\n"
    "    @Volatile\n"
    "    private var pingTags: Map<String, String> = emptyMap()\n\n"
    "    /** Тег outbound'а для замера пинга конкретного сервера. */\n"
    "    fun pingTag(serverName: String): String? = pingTags[serverName]",
    "pingTags",
)

# 2. start принимает всю подписку: из неё строятся замеры.
sub(
    "    fun start(xrayJson: String, allowInstall: Boolean = true) {",
    "    fun start(xrayJson: String, rawSubscription: String = \"\", allowInstall: Boolean = true) {",
    "сигнатура start",
)

# 3. Передаём подписку в конфиг и запоминаем её.
sub(
    "                val config = coreConfig(xrayJson, shimPort, tun)",
    "                val config = coreConfig(xrayJson, shimPort, tun, raw = rawSubscription)",
    "coreConfig (процесс)",
)
sub(
    "                Store.lastConfig(xrayJson)",
    "                Store.lastConfig(xrayJson)\n"
    "                if (rawSubscription.isNotBlank()) Store.lastRaw(rawSubscription)",
    "запоминание подписки",
)

# 4. Привилегированный путь: то же самое.
sub(
    "        val config = coreConfig(xrayJson, shimPort, useTun = true, privileged = true)",
    "        val config = coreConfig(\n"
    "            xrayJson, shimPort, useTun = true, privileged = true,\n"
    "            raw = Store.lastRaw(),\n"
    "        )",
    "coreConfig (демон)",
)

# 5. Восстановление: перезапускаем с сохранённой подпиской, чтобы были замеры.
sub(
    "        // Восстановление не должно внезапно спрашивать пароль: системный режим\n"
    "        // ставится только по явному нажатию орла.\n"
    "        start(config, allowInstall = false)",
    "        // Восстановление не должно внезапно спрашивать пароль: системный режим\n"
    "        // ставится только по явному нажатию орла. Подписку передаём целиком —\n"
    "        // без неё в конфиге не будет outbound'ов для пинга остальных серверов.\n"
    "        start(config, Store.lastRaw(), allowInstall = false)",
    "resumeSaved",
)

# 6. coreConfig: параметр raw + добавление outbound'ов замера.
sub(
    "    internal fun coreConfig(xrayJson: String, shimPort: Int, useTun: Boolean, privileged: Boolean = false): String {",
    "    internal fun coreConfig(\n"
    "        xrayJson: String,\n"
    "        shimPort: Int,\n"
    "        useTun: Boolean,\n"
    "        privileged: Boolean = false,\n"
    "        raw: String = \"\",\n"
    "    ): String {",
    "сигнатура coreConfig",
)

sub(
    """        val base = JSONObject(toSingBox(xrayJson, options))
        base.put("inbounds", org.json.JSONArray().put(inbound(useTun)))""",
    """        val base = JSONObject(toSingBox(xrayJson, options))
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
        }""",
    "добавление замеров",
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("TunnelController: замеры подключены")
