import glob
import io

phrases = {
    "автоустановка": "установка системного режима",
    "раздел настроек": "СИСТЕМНЫЙ РЕЖИМ",
    "давняя строка": "туннель остановлен",
    "установщик": "macAdminCommand",
}

found = {name: [] for name in phrases}
for path in glob.glob("/tmp/wj/**/*.class", recursive=True):
    data = io.open(path, "rb").read()
    for name, phrase in phrases.items():
        if phrase.encode("utf-8") in data:
            found[name].append(path)

for name, paths in found.items():
    print(f"{name}: {len(paths)} файл(ов) {paths[:2]}")
