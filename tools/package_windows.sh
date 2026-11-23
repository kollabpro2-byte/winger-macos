#!/usr/bin/env bash
# Собирает portable-пакет Winger VPN для Windows x64.
#
# jpackage умеет собирать MSI только на Windows (кросс-сборки нет), поэтому
# пакет здесь «portable»: jar приложения + JRE Windows + ядро, запуск через
# run.bat. Такой архив работает сразу после распаковки и проверяется на любом
# компьютере с Windows 10/11 x64.
#
# Запуск: tools/package_windows.sh [--skip-build]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DESKTOP="$ROOT/winger-desktop"
DIST="$ROOT/dist"
OUT="$DIST/winger-windows"
ZIP="$DIST/winger-vpn-windows-x64.zip"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
GRADLE="${GRADLE:-$HOME/.local/gradle-8.7/bin/gradle}"

# 1. Свежие jar: приложение + зависимости.
if [[ "${1:-}" != "--skip-build" ]]; then
  ( cd "$DESKTOP" && JAVA_HOME="$JAVA_HOME" "$GRADLE" --no-daemon -q portablePayload )
fi

rm -rf "$OUT"
mkdir -p "$OUT"/{app,core,jre}

cp -R "$DESKTOP/build/portable/app" "$OUT"/

# 1b. Skiko — нативная графическая библиотека Compose. Gradle положил версию для
# текущей ОС (macOS), а для Windows нужен свой рантайм: заменяем артефакт.
SKIKO_VERSION="0.8.18"
SKIKO_WIN="skiko-awt-runtime-windows-x64-$SKIKO_VERSION.jar"
rm -f "$OUT"/app/lib/skiko-awt-runtime-macos-*.jar "$OUT"/app/lib/skiko-awt-runtime-linux-*.jar
if [[ ! -f "$DIST/$SKIKO_WIN" ]]; then
  curl -fsSL -o "$DIST/$SKIKO_WIN" \
    "https://repo.maven.apache.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-x64/$SKIKO_VERSION/$SKIKO_WIN"
fi
cp "$DIST/$SKIKO_WIN" "$OUT/app/lib/"

# 2. Ядро sing-box-lx для Windows (то же семейство, что libbox в Android-сборке).
unzip -q -o "$DIST/win-core.zip" -d "$DIST/.core-tmp"
cp "$DIST"/.core-tmp/sing-box-*/sing-box.exe "$OUT/core/"
# libcronet.dll нужен ядру рядом с exe (WebRTC/Cronet-транспорт).
if compgen -G "$DIST/.core-tmp/sing-box-*/libcronet.dll" > /dev/null; then
  cp "$DIST"/.core-tmp/sing-box-*/libcronet.dll "$OUT/core/"
fi
cp "$DIST"/.core-tmp/sing-box-*/LICENSE "$OUT/core/LICENSE-sing-box.txt" 2>/dev/null || true
rm -rf "$DIST/.core-tmp"

# 3. wintun.dll — драйвер виртуального адаптера для tun-режима.
unzip -q -o "$DIST/wintun.zip" 'wintun/bin/amd64/wintun.dll' -d "$DIST/.wintun-tmp"
cp "$DIST/.wintun-tmp/wintun/bin/amd64/wintun.dll" "$OUT/core/"
rm -rf "$DIST/.wintun-tmp"

# 4. JRE для Windows x64 — ставить Java пользователю не нужно.
unzip -q -o "$DIST/jre-win.zip" -d "$DIST/.jre-tmp"
cp -R "$DIST"/.jre-tmp/jdk-*-jre/. "$OUT/jre/"
rm -rf "$DIST/.jre-tmp"

# 5. Скрипты запуска и описание.
cp "$DESKTOP"/packaging/run.bat "$OUT/"
cp "$DESKTOP"/packaging/run-no-admin.bat "$OUT/"
cp "$DESKTOP"/packaging/run-console.bat "$OUT/"
cp "$DESKTOP"/packaging/README-WINDOWS.txt "$OUT/README.txt"

# 6. Архив: имя каталога внутри — WingerVPN, чтобы распаковка была предсказуемой.
rm -f "$ZIP"
( cd "$DIST" && mv winger-windows WingerVPN && zip -qr "$(basename "$ZIP")" WingerVPN && mv WingerVPN winger-windows )

echo "готово: $ZIP"
du -sh "$ZIP" | awk '{print "размер:", $1}'
