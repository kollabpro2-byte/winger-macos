#!/usr/bin/env python3
"""Api: Android-специфику (Context/Build/Base64/Handler) заменяет на JVM-аналоги."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/net/Api.kt"

REPLACEMENTS = [
    ("import android.annotation.SuppressLint\n"
     "import android.content.Context\n"
     "import android.os.Build\n",
     "import pro.wingerapp.vpn.Platform\n"),
    ("import java.security.MessageDigest\n",
     "import java.security.MessageDigest\nimport java.util.Base64\n"),
    ("@SuppressLint(\"StaticFieldLeak\")\nobject Api {", "object Api {"),
    ("    /**\n"
     "     * Application context — не утечка: живёт столько же, сколько процесс.\n"
     "     * Присваивается ровно один раз в [init] из `applicationContext`.\n"
     "     */\n"
     "    @SuppressLint(\"StaticFieldLeak\")\n"
     "    lateinit var app: Context\n\n", ""),
    ("    private val main = android.os.Handler(android.os.Looper.getMainLooper())\n\n", ""),
    ("    fun init(ctx: Context) {\n        app = ctx.applicationContext\n",
     "    fun init() {\n"),
    ("            (::app.isInitialized && Store.cookies().isNotBlank())",
     "            Store.cookies().isNotBlank()"),
    ("        if (::app.isInitialized) Store.logout()", "        Store.logout()"),
    ("            main.post { runCatching { onUnauthorized?.invoke() } }",
     "            // Колбэк должен прийти в UI-поток: на десктопе это EDT.\n"
     "            javax.swing.SwingUtilities.invokeLater { runCatching { onUnauthorized?.invoke() } }"),
    ("    private fun persist() {\n        if (!::app.isInitialized) return\n",
     "    private fun persist() {\n"),
    ("runCatching { android.util.Base64.decode(encoded, android.util.Base64.DEFAULT) }",
     "runCatching { Base64.getMimeDecoder().decode(encoded) }"),
    ("android.util.Base64.encodeToString(file.bytes, android.util.Base64.NO_WRAP)",
     "Base64.getEncoder().encodeToString(file.bytes)"),
    ("        val rawMaker = Build.MANUFACTURER.orEmpty()\n"
     "        val maker = if (rawMaker.isEmpty()) \"\" else rawMaker.substring(0, 1).uppercase() + rawMaker.substring(1)\n"
     "        val model = Build.MODEL.orEmpty()\n"
     "        val device = if (model.startsWith(maker, true)) model else \"$maker $model\"\n",
     "        // Панель узнаёт клиента по UA, поэтому он остаётся как у Android-сборки:\n"
     "        // так пользователь получает ту же подписку и тот же набор серверов.\n"
     "        // Моделью устройства служит имя компьютера.\n"
     "        val model = System.getenv(\"COMPUTERNAME\")?.takeIf { it.isNotBlank() }\n"
     "            ?: System.getProperty(\"os.name\").orEmpty()\n"
     "        val device = \"Winger $model\"\n"),
    ("setRequestProperty(\"x-ver-os\", Build.VERSION.RELEASE)",
     "setRequestProperty(\"x-ver-os\", System.getProperty(\"os.version\").orEmpty())"),
]

text = io.open(PATH, encoding="utf-8").read()
for old, new in REPLACEMENTS:
    if old not in text:
        raise SystemExit("не найдено: " + old[:80].replace("\n", "\\n"))
    text = text.replace(old, new)

io.open(PATH, "w", encoding="utf-8").write(text)
print("Api обновлён")
