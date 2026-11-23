#!/usr/bin/env bash
# Скачивает всё, что нужно для Windows-сборки, в dist/ (один раз).
#
# Ядро — sing-box-lx 1.14.4: то же семейство, что libbox в Android-сборке,
# поэтому конфиг приложения принимается без правок. JRE — Eclipse Temurin 21
# (Windows x64), чтобы пользователю не нужно было ставить Java.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="$ROOT/dist"
mkdir -p "$DIST"

CORE_URL="https://github.com/Leadaxe/sing-box-lx/releases/download/v1.14.4-lx.35/sing-box-1.14.4-lx.35-windows-amd64.zip"
JRE_URL="https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"
WINTUN_URL="https://www.wintun.net/builds/wintun-0.14.1.zip"
SKIKO="skiko-awt-runtime-windows-x64-0.8.18.jar"

fetch() {
  local url="$1" target="$2"
  if [[ -f "$target" && -s "$target" ]]; then
    echo "уже есть: $(basename "$target")"
    return
  fi
  echo "качаю: $(basename "$target")"
  curl -fL --retry 3 -o "$target" "$url"
}

fetch "$CORE_URL" "$DIST/win-core.zip"
fetch "$JRE_URL" "$DIST/jre-win.zip"
fetch "$WINTUN_URL" "$DIST/wintun.zip"
fetch "https://repo.maven.apache.org/maven2/org/jetbrains/skiko/skiko-awt-runtime-windows-x64/0.8.18/$SKIKO" \
  "$DIST/$SKIKO"

echo "готово: $DIST"
