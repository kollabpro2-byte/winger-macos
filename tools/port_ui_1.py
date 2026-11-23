#!/usr/bin/env python3
"""UI, часть 1/2: Components и AccountTab → десктопный платформенный слой.

Скрипт идемпотентен: повторный запуск не ломает уже перенесённые места.
"""
import io
import re

UI = "src/main/kotlin/pro/wingerapp/vpn/ui/"


def patch(name, replacements):
    path = UI + name
    text = io.open(path, encoding="utf-8").read()
    for old, new in replacements:
        if old in text:
            text = text.replace(old, new)  # replace() заменяет все вхождения
        elif new not in text:
            raise SystemExit(f"{name}: не найдено -> {old[:70]!r}")
    io.open(path, "w", encoding="utf-8").write(text)
    print(f"{name}: ok")


PICK_OLD = """    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val added = withContext(Dispatchers.IO) { uris.mapNotNull { readAttachment(ctx, it) } }
            files = files + added
        }
    }"""

PICK_NEW = """    val pick: () -> Unit = {
        scope.launch {
            val added = withContext(Dispatchers.IO) { pickAttachments() }
            files = files + added
        }
    }"""

EAGLE_OLD = """@Composable
internal fun rememberEagle(): ImageBitmap {
    val res = LocalContext.current.resources
    return remember(res) {
        // Один декодированный растр на всё приложение: орёл нужен и в круге,
        // и на экране входа, и в карточке подписки — три отдельных декодирования
        // 300×230 px были лишней работой и памятью.
        eagleCache ?: run {
            val opts = BitmapFactory.Options().apply { inScaled = false }
            BitmapFactory.decodeResource(res, R.drawable.eagle, opts).asImageBitmap()
                .also { eagleCache = it }
        }
    }
}"""

EAGLE_NEW = """@Composable
internal fun rememberEagle(): ImageBitmap = remember(Unit) {
    // Один декодированный растр на всё приложение: орёл нужен и в круге, и на
    // экране входа, и в карточке подписки.
    eagleCache ?: loadBitmap(DRAWABLE_PATHS.getValue(R.drawable.eagle)).also { eagleCache = it }
}"""

patch("Components.kt", [(EAGLE_OLD, EAGLE_NEW)])

components = io.open(UI + "Components.kt", encoding="utf-8").read()
components = re.sub(
    r"internal fun readAttachment\(ctx: Context, uri: Uri\): pro\.wingerapp\.vpn\.net\.Attached\? = runCatching \{.*?\n\}\.getOrNull\(\)",
    "// На десктопе файл читается напрямую: pickAttachments() сразу отдаёт байты\n"
    "// (см. ui/Res.kt), поэтому ветка с content:// больше не нужна.",
    components,
    flags=re.S,
)
io.open(UI + "Components.kt", "w", encoding="utf-8").write(components)
print("Components.kt: readAttachment -> pickAttachments")

patch("AccountTab.kt", [
    ("internal fun AccountTab(ctx: Context, hwid: String, onLogout: () -> Unit, onSupport: () -> Unit) {",
     "internal fun AccountTab(hwid: String, onLogout: () -> Unit, onSupport: () -> Unit) {"),
    (PICK_OLD, PICK_NEW),
    ("ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))",
     "Platform.openUrl(url)"),
    ("ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))",
     "Platform.openUrl(result)"),
    ("internal fun SupportChat(ctx: Context, onClose: () -> Unit) {",
     "internal fun SupportChat(onClose: () -> Unit) {"),
    ("mapOf<String, android.graphics.Bitmap>()", "mapOf<String, androidx.compose.ui.graphics.ImageBitmap>()"),
    ("val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@mapNotNull null",
     "val bmp = decodeImage(bytes) ?: return@mapNotNull null"),
    ("Image(bmp.asImageBitmap(), file.name,", "Image(bmp, file.name,"),
    ('pick.launch(arrayOf("image/png", "image/jpeg", "image/webp", "application/pdf"))', "pick()"),
])

print("часть 1 готова")
