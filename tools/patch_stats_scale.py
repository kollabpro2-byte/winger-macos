#!/usr/bin/env python3
"""Карточка «осталось / устройства / трафик» растёт вместе с шириной окна."""
import io

PATH = "src/main/kotlin/pro/wingerapp/vpn/ui/HomeTab.kt"
text = io.open(PATH, encoding="utf-8").read()


def sub(old, new, label):
    global text
    if old not in text:
        raise SystemExit(f"не найдено: {label}")
    text = text.replace(old, new, 1)


sub(
    """@Composable
private fun StatsRow(days: String, devices: String, traffic: String) {
    val s = rememberStrings()
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).clip(RoundedCornerShape(18.dp))
            .background(Elev).border(1.dp, CardLine, RoundedCornerShape(18.dp))
            .padding(vertical = 5.4.dp, horizontal = 2.dp),""",
    """@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun StatsRow(days: String, devices: String, traffic: String) {
    val s = rememberStrings()
    // На большом окне карточка не должна выглядеть мелкой: телефонные 11.7 sp на
    // 1400 px читаются плохо, поэтому подписи и значения растут с шириной.
    val widthDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width / density
    }
    val scale = when {
        widthDp >= 1200f -> 1.35f
        widthDp >= 900f -> 1.2f
        else -> 1f
    }
    Row(
        Modifier.fillMaxWidth().padding(top = (14f * scale).dp).clip(RoundedCornerShape(18.dp))
            .background(Elev).border(1.dp, CardLine, RoundedCornerShape(18.dp))
            .padding(vertical = (5.4f * scale).dp, horizontal = 2.dp),""",
    "StatsRow шапка",
)

sub(
    """        Stat(Modifier.weight(1f), StatKind.Clock, c1, c1.copy(alpha = .12f), s[R.string.home_days_left], days)
        VDivider()
        Stat(Modifier.weight(1f), StatKind.Phone, c2, c2.copy(alpha = .14f), s[R.string.home_devices_label], devices)
        VDivider()
        Stat(Modifier.weight(1f), StatKind.Traffic, c3, c3.copy(alpha = .14f), s[R.string.home_traffic_label], traffic, unit = s[R.string.fmt_gb_unit])""",
    """        Stat(Modifier.weight(1f), StatKind.Clock, c1, c1.copy(alpha = .12f), s[R.string.home_days_left], days, scale = scale)
        VDivider(scale)
        Stat(Modifier.weight(1f), StatKind.Phone, c2, c2.copy(alpha = .14f), s[R.string.home_devices_label], devices, scale = scale)
        VDivider(scale)
        Stat(
            Modifier.weight(1f), StatKind.Traffic, c3, c3.copy(alpha = .14f),
            s[R.string.home_traffic_label], traffic, unit = s[R.string.fmt_gb_unit], scale = scale,
        )""",
    "вызовы Stat",
)

sub(
    """@Composable
private fun VDivider() {
    Box(Modifier.width(1.dp).height(26.dp).background(Line))
}""",
    """@Composable
private fun VDivider(scale: Float = 1f) {
    Box(Modifier.width(1.dp).height((26f * scale).dp).background(Line))
}""",
    "VDivider",
)

sub(
    "private fun Stat(modifier: Modifier, kind: StatKind, tint: Color, bg: Color, label: String, value: String, unit: String? = null) {",
    "private fun Stat(\n"
    "    modifier: Modifier, kind: StatKind, tint: Color, bg: Color, label: String, value: String,\n"
    "    unit: String? = null, scale: Float = 1f,\n"
    ") {",
    "сигнатура Stat",
)

sub(
    """    Row(modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {""",
    """    Row(modifier.padding(horizontal = (6f * scale).dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size((22f * scale).dp).clip(RoundedCornerShape((8f * scale).dp)).background(bg),
            contentAlignment = Alignment.Center,
        ) {""",
    "иконка Stat",
)

sub(
    """        Column(Modifier.padding(start = 6.dp).weight(1f)) {""",
    """        Column(Modifier.padding(start = (6f * scale).dp).weight(1f)) {""",
    "отступ подписи",
)

sub(
    """            Text(label, color = Muted, fontSize = 11.7.sp, letterSpacing = .1.sp, maxLines = 1, overflow = TextOverflow.Clip)""",
    """            Text(
                label, color = Muted, fontSize = (11.7f * scale).sp,
                letterSpacing = .1.sp, maxLines = 1, overflow = TextOverflow.Clip,
            )""",
    "подпись",
)

sub(
    """                val size = fitValueSize(whole, suffix, maxWidth, 12.5.sp, 8.sp)""",
    """                val size = fitValueSize(whole, suffix, maxWidth, (12.5f * scale).sp, (8f * scale).sp)""",
    "кегль значения",
)

io.open(PATH, "w", encoding="utf-8").write(text)
print("HomeTab: масштаб статистики")
