package pro.wingerapp.vpn.ui

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import pro.wingerapp.vpn.FONT_PATHS
import pro.wingerapp.vpn.R

/**
 * Inter с настоящими весами.
 *
 * Та же причина, что и на Android: системный шрифт не имеет промежуточных весов,
 * и 600 Compose «дорисовывает» синтетически — жирный текст выглядит размазанным.
 * На десктопе шрифт грузится из ресурсов приложения (см. `tools/gen_resources.py`),
 * поэтому вид одинаков на Windows, macOS и Linux.
 */
val WingerSans = FontFamily(
    Font(FONT_PATHS.getValue(R.font.inter_regular), FontWeight.Normal),
    Font(FONT_PATHS.getValue(R.font.inter_medium), FontWeight.Medium),
    Font(FONT_PATHS.getValue(R.font.inter_semibold), FontWeight.SemiBold),
    Font(FONT_PATHS.getValue(R.font.inter_bold), FontWeight.Bold),
    Font(FONT_PATHS.getValue(R.font.inter_extrabold), FontWeight.ExtraBold),
)

/**
 * Та же тема, но со шрифтом Inter во всех стилях Material: компоненты (диалоги,
 * кнопки, поля ввода) берут шрифт из Theme.typography, а не из LocalTextStyle.
 */
private fun Typography.withFamily(f: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall = displaySmall.copy(fontFamily = f),
    headlineLarge = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall = headlineSmall.copy(fontFamily = f),
    titleLarge = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall = titleSmall.copy(fontFamily = f),
    bodyLarge = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall = bodySmall.copy(fontFamily = f),
    labelLarge = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall = labelSmall.copy(fontFamily = f),
)

@Composable
fun WingerFonts(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = WingerSans),
    ) {
        MaterialTheme(typography = MaterialTheme.typography.withFamily(WingerSans)) {
            content()
        }
    }
}
