package pro.wingerapp.vpn.ui

import androidx.compose.runtime.Composable
import java.util.Locale
import pro.wingerapp.vpn.EN_PLURALS
import pro.wingerapp.vpn.EN_STRINGS
import pro.wingerapp.vpn.RU_PLURALS
import pro.wingerapp.vpn.RU_STRINGS

/**
 * Localised text for the whole UI.
 *
 * On Android the values came from `Resources`. On desktop they are replaced by
 * tables generated from the same `strings.xml` (see the appendix in `R.kt`), so
 * call sites stay untouched: the same `s[R.string.x]`, `format`, `quantity`.
 */
@Composable
fun rememberStrings(): Strings = Strings(english = isEnglish())

private fun isEnglish(): Boolean = Locale.getDefault().language.equals("en", ignoreCase = true)

/** Strings outside a composable scope (coroutines, click handlers). */
fun stringsNow(): Strings = Strings(english = isEnglish())

class Strings(private val english: Boolean = isEnglish()) {
    private val table = if (english) EN_STRINGS else RU_STRINGS
    private val pluralTable = if (english) EN_PLURALS else RU_PLURALS
    private val locale: Locale = if (english) Locale.ENGLISH else Locale("ru")

    operator fun get(id: Int): String = table[id] ?: ""

    fun format(id: Int, vararg args: Any): String =
        String.format(locale, table[id] ?: "", *args)

    /** Plurals: `quantity(R.plurals.days, 5)` → «5 дней» / «5 days». */
    fun quantity(id: Int, n: Long): String {
        val forms = pluralTable[id] ?: return ""
        val form = forms[pluralForm(n)] ?: forms["other"] ?: ""
        return String.format(locale, form, n)
    }

    /** Decimal number in the UI locale: «1,2» in ru, «1.2» in en. */
    fun number(value: Double, decimals: Int = 1): String =
        String.format(locale, "%.${decimals}f", value)

    private fun pluralForm(n: Long): String {
        val mod10 = n % 10
        val mod100 = n % 100
        return if (english) {
            if (n == 1L) "one" else "other"
        } else when {
            mod10 == 1L && mod100 != 11L -> "one"
            mod10 in 2..4 && mod100 !in 12..14 -> "few"
            else -> "many"
        }
    }
}
