#!/usr/bin/env python3
"""DNS-скоупы подписки → dns.rules с rule_set; inboundTag не считаем потерей."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/RoutingPlan.kt"
text = io.open(PATH, encoding="utf-8").read()


def sub(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)


sub(
    '        val dns = convertDns(xray.optJSONObject("dns"), skipped)',
    '        val dns = convertDns(xray.optJSONObject("dns"), skipped, ruleSets, seenSets)',
    "вызов convertDns",
)

sub(
    "    private fun convertDns(dns: JSONObject?, skipped: MutableList<String>): JSONObject? {",
    "    private fun convertDns(\n"
    "        dns: JSONObject?,\n"
    "        skipped: MutableList<String>,\n"
    "        ruleSets: JSONArray,\n"
    "        seenSets: MutableSet<String>,\n"
    "    ): JSONObject? {",
    "сигнатура convertDns",
)

sub(
    """            val matchers = JSONObject()
            if (domainMatchers(s.optJSONArray("domains"), matchers, skipped)) {
                // Правило DNS: этим доменам — этот сервер.
                rules.put(JSONObject(matchers.toString()).put("server", tag))
            } else if (finalTag == null) {""",
    """            val matchers = JSONObject()
            val sets = JSONArray()
            val hasDomains = domainMatchers(
                s.optJSONArray("domains"), matchers, skipped, sets, ruleSets, seenSets,
            )
            // geosite-скоупы («apple», «category-ru») переводим в rule_set: ядро
            // скачает набор и будет резолвить эти домены тем сервером, который
            // задумал автор подписки.
            if (sets.length() > 0) matchers.put("rule_set", sets)
            if (hasDomains || sets.length() > 0) {
                // Правило DNS: этим доменам — этот сервер.
                rules.put(JSONObject(matchers.toString()).put("server", tag))
            } else if (finalTag == null) {""",
    "блок matchers в convertDns",
)

sub(
    """        if (xray.has("inboundTag")) {
            skipped += "inboundTag"
            return
        }""",
    """        if (xray.has("inboundTag")) {
            // Правило привязано к входящему тегу Xray (api/dns-in). У нас один
            // вход tun-in, поэтому правило неприменимо — это нормальный пропуск,
            // а не потеря; в счётчик «не перенесено» его не добавляем.
            return
        }""",
    "inboundTag",
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("RoutingPlan: DNS-скоупы добавлены")
