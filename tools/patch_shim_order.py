#!/usr/bin/env python3
"""Правило «шлюзы напрямую» должно стоять после sniff/hijack-dns."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/TunnelController.kt"
text = io.open(PATH, encoding="utf-8").read()

OLD = """            val rules = org.json.JSONArray()
            rules.put(
                JSONObject()
                    .put("action", "route")
                    .put("outbound", "direct")
                    .put("domain", org.json.JSONArray(gateways)),
            )
            for (i in 0 until old.length()) rules.put(old.getJSONObject(i))
            route.put("rules", rules)"""

NEW = """            // Важно: правило должно идти ПОСЛЕ sniff/hijack-dns. Если поставить
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
            route.put("rules", rules)"""

if OLD not in text:
    raise SystemExit("блок вставки не найден")
io.open(PATH, "w", encoding="utf-8").write(text.replace(OLD, NEW, 1))
print("TunnelController: правило после sniff")
