package pro.wingerapp.vpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import org.jetbrains.skia.Image
import pro.wingerapp.vpn.DRAWABLE_PATHS
import pro.wingerapp.vpn.net.Attached
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Растр из ресурсов classpath.
 *
 * На Android картинки лежали в `res/drawable` и декодировались `BitmapFactory`.
 * На десктопе те же файлы лежат в `src/main/resources/drawable` (см.
 * `tools/gen_resources.py`), поэтому читаем их по имени.
 */
internal fun loadBitmap(path: String): ImageBitmap =
    requireNotNull(object {}.javaClass.classLoader?.getResourceAsStream(path)) {
        "нет ресурса $path"
    }.use { loadImageBitmap(it) }

/** Замена android `painterResource(id)`: id → путь в resources. */
@Composable
internal fun painterResource(id: Int): Painter {
    val path = DRAWABLE_PATHS[id] ?: error("неизвестный drawable: $id")
    return remember(id) { BitmapPainter(loadBitmap(path)) }
}

/** Картинка из байтов (вложения поддержки): аналог `BitmapFactory.decodeByteArray`. */
internal fun decodeImage(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

/**
 * Файлы для чата поддержки — аналог Android-пикера документов.
 *
 * Возвращает уже прочитанные байты: у десктопа нет `content://`-URI, файл
 * доступен напрямую, а лимит 8 МБ тот же, что в мобильном клиенте.
 */
internal fun pickAttachments(): List<Attached> {
    val chooser = JFileChooser().apply {
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter(
            "Изображения и PDF", "png", "jpg", "jpeg", "webp", "gif", "pdf",
        )
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return emptyList()
    return chooser.selectedFiles.mapNotNull { readAttachment(it) }
}

/** Один файл: имя, MIME по расширению и байты (не больше 8 МБ, как на Android). */
internal fun readAttachment(file: File): Attached? = runCatching {
    val bytes = file.readBytes()
    if (bytes.size > 8 * 1024 * 1024) return null
    Attached(file.name, mimeOf(file.name), bytes)
}.getOrNull()

private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}
