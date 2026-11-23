package pro.wingerapp.vpn

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Всё, что на Android давал `Context`: каталог данных, буфер обмена, открытие
 * ссылок, выбор файлов, проверка сети.
 *
 * На Windows данные лежат в `%APPDATA%\WingerVPN` — это то же место, куда
 * ставит свои файлы обычная программа, и оно переживает обновление.
 */
object Platform {
    val osName: String = System.getProperty("os.name").orEmpty()
    val isWindows: Boolean = osName.startsWith("Windows", ignoreCase = true)
    val isMac: Boolean = osName.startsWith("Mac", ignoreCase = true)

    /** Каталог с настройками, логом и ядром. Создаётся при первом обращении. */
    val dataDir: File by lazy {
        val home = System.getProperty("user.home").orEmpty()
        val path = when {
            isWindows -> System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
                ?.let { File(it, "WingerVPN") } ?: File(home, "WingerVPN")
            isMac -> File(home, "Library/Application Support/WingerVPN")
            else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "winger")
        }
        path.apply { mkdirs() }
    }

    /** Кэш и рабочие файлы (ядро, лог) — рядом с настройками. */
    fun file(name: String): File = File(dataDir, name)

    fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }

    fun copyText(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }

    /**
     * Есть ли сеть. Проверяем коротким TCP-коннектом, а не резолвом имени:
     * DNS может отвечать из кэша при полностью отрезанном канале.
     */
    fun isOnline(timeoutMs: Int = 900): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 443), timeoutMs) }
        true
    }.getOrDefault(false)

    /** Выбор картинок для аватарки устройства — аналог Android-пикера. */
    fun pickImages(parent: java.awt.Component? = null): List<PickedImage> {
        val chooser = JFileChooser().apply {
            isMultiSelectionEnabled = true
            fileFilter = FileNameExtensionFilter("Изображения", "png", "jpg", "jpeg", "webp")
        }
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return emptyList()
        return chooser.selectedFiles.mapNotNull { file ->
            runCatching {
                PickedImage(file.name, SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap())
            }.getOrNull()
        }
    }
}

/** Файл, выбранный пользователем: имя для списка устройств и готовый растр. */
data class PickedImage(val name: String, val bitmap: ImageBitmap)
