#!/usr/bin/env bash
# Собирает portable-пакет Winger VPN для Windows x64 с настоящим WingerVPN.exe.
#
# jpackage (MSI) требует Windows-хост, поэтому лаунчер делает Launch4j — он
# собирает .exe на macOS. Внутри: exe + lib (jar'ы) + jre (Windows) + core.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DESKTOP="$ROOT/winger-desktop"
DIST="$ROOT/dist"
OUT="$DIST/winger-windows"
ZIP="$DIST/winger-vpn-windows-x64.zip"
JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk21/Contents/Home}"
GRADLE="${GRADLE:-$HOME/.local/gradle-8.7/bin/gradle}"

# 1. jar'ы и .exe-лаунчер.
if [[ "${1:-}" != "--skip-build" ]]; then
  ( cd "$DESKTOP" && JAVA_HOME="$JAVA_HOME" "$GRADLE" --no-daemon -q exeJar createExe portablePayload )
fi

rm -rf "$OUT"
mkdir -p "$OUT"/{lib,core,jre}

cp "$DESKTOP/build/launch4j/WingerVPN.exe" "$OUT/WingerVPN.exe"
cp "$DESKTOP/build/libs/winger-app.jar" "$OUT/lib/"

# 2. Библиотеки: убираем macOS-рантайм Skiko и ставим Windows-рантайм.
cp "$DESKTOP/build/portable/app/lib/"*.jar "$OUT/lib/"
SKIKO_WIN="skiko-awt-runtime-windows-x64-0.8.18.jar"
rm -f "$OUT"/lib/skiko-awt-runtime-macos-*.jar "$OUT"/lib/skiko-awt-runtime-linux-*.jar
if [[ ! -f "$DIST/$SKIKO_WIN" ]]; then
  curl -fsSL -o "$DIST/$SKIKO_WIN" \
    "https://repo.maven.apache.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-x64/0.8.18/$SKIKO_WIN"
fi
cp "$DIST/$SKIKO_WIN" "$OUT/lib/"

# 3. Ядро sing-box-lx для Windows.
unzip -q -o "$DIST/win-core.zip" -d "$DIST/.core-tmp"
cp "$DIST"/.core-tmp/sing-box-*/sing-box.exe "$OUT/core/"
if compgen -G "$DIST/.core-tmp/sing-box-*/libcronet.dll" > /dev/null; then
  cp "$DIST"/.core-tmp/sing-box-*/libcronet.dll "$OUT/core/"
fi
cp "$DIST"/.core-tmp/sing-box-*/LICENSE "$OUT/core/LICENSE-sing-box.txt" 2>/dev/null || true
rm -rf "$DIST/.core-tmp"

# 4. wintun.dll — драйвер виртуального адаптера.
unzip -q -o "$DIST/wintun.zip" 'wintun/bin/amd64/wintun.dll' -d "$DIST/.wintun-tmp"
cp "$DIST/.wintun-tmp/wintun/bin/amd64/wintun.dll" "$OUT/core/"
rm -rf "$DIST/.wintun-tmp"

# 5. JRE для Windows x64: ставить Java пользователю не нужно.
unzip -q -o "$DIST/jre-win.zip" -d "$DIST/.jre-tmp"
cp -R "$DIST"/.jre-tmp/jdk-*-jre/. "$OUT/jre/"
rm -rf "$DIST/.jre-tmp"

# 6. Запасной запуск (если exe заблокирован антивирусом) и описание.
cp "$DESKTOP"/packaging/run.bat "$OUT/"
cp "$DESKTOP"/packaging/run-no-admin.bat "$OUT/"
cp "$DESKTOP"/packaging/run-console.bat "$OUT/"
cp "$DESKTOP"/packaging/README-WINDOWS.txt "$OUT/README.txt"

# 7. Архив: внутри папка WingerVPN.
rm -f "$ZIP"
( cd "$DIST" && mv winger-windows WingerVPN && zip -qr "$(basename "$ZIP")" WingerVPN && mv WingerVPN winger-windows )

echo "готово: $ZIP"
du -sh "$ZIP" | awk '{print "размер:", $1}'
