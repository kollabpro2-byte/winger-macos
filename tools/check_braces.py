import io
import re

text = io.open("build.gradle.kts", encoding="utf-8").read()

# Грубо вырезаем строки и комментарии, чтобы считать только структурные скобки.
text = re.sub(r"//[^\n]*", "", text)
text = re.sub(r'"(?:[^"\\]|\\.)*"', '""', text)

depth = 0
for number, line in enumerate(text.split("\n"), 1):
    opens = line.count("{")
    closes = line.count("}")
    depth += opens - closes
    if 25 <= number <= 78:
        print(f"{number:3} {depth:2} {line[:70]}")
print("итоговая глубина:", depth)
