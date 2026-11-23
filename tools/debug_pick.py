import io

path = "src/main/kotlin/pro/wingerapp/vpn/ui/AccountTab.kt"
text = io.open(path, encoding="utf-8").read()
needle = "val pick = rememberLauncherForActivityResult"
idx = text.find(needle)
print("найдено:", idx)
print(repr(text[idx - 20:idx + 260]))
