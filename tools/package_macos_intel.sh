#!/usr/bin/env bash
# Сборка Intel-версии (x86_64) для Mac. jpackage не умеет кросс-сборку с Apple
# Silicon, поэтому .app собирается вручную: jar-ы + JRE x64 + ядро darwin-amd64.
#
# Итог: dist/WingerVPN-1.0.75-intel.dmg — работает на MacBook 2015+ (macOS 11+).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DESKTOP="$ROOT/winger-desktop"
DIST="$ROOT/dist"
JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk21/Contents/Home}"
GRADLE="${GRADLE:-$HOME/.local/gradle-8.7/bin/gradle}"
STAGE="$DIST/.intel-stage"
APP="$STAGE/WingerVPN.app"

# 1. jar-ы приложения.
( cd "$DESKTOP" && JAVA_HOME="$JAVA_HOME" "$GRADLE" --no-daemon -q portablePayload )

# 2. JRE x64 (Temurin 21, minos 11.0) и ядро darwin-amd64.
if [[ ! -d "$DIST/jre-x64" ]]; then
  mkdir -p "$DIST/jre-x64"
  tar -xzf "$DIST/jre-mac-x64.zip" -C "$DIST/jre-x64"
fi
JRE_SRC="$(ls -d "$DIST"/jre-x64/*/Contents/Home | head -1)"

if [[ ! -x "$DIST/mac-x64/sing-box" ]]; then
  mkdir -p "$DIST/mac-x64"
  tar -xzf "$DIST/mac-x64-core.tar.gz" -C "$DIST/mac-x64" --strip-components=1
fi

# 3. Скелет бандла.
rm -rf "$STAGE"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/app" "$APP/Contents/core" "$APP/Contents/jre" "$APP/Contents/Resources"
cp -R "$DESKTOP/build/portable/app/." "$APP/Contents/app/"
cp -R "$JRE_SRC/." "$APP/Contents/jre/"
cp "$DIST/mac-x64/sing-box" "$APP/Contents/core/sing-box"
cp "$DESKTOP/icons/winger.icns" "$APP/Contents/Resources/AppIcon.icns"
chmod +x "$APP/Contents/core/sing-box"

# Skiko — нативная графика: в Intel-сборке нужен x64-рантайм, arm64 лишний.
rm -f "$APP"/Contents/app/lib/skiko-awt-runtime-macos-arm64-*.jar
cp "$DIST/skiko-awt-runtime-macos-x64-0.8.18.jar" "$APP/Contents/app/lib/"

# 4. Запуск: -XstartOnFirstThread обязателен для AWT/Compose на macOS.
cat > "$APP/Contents/MacOS/WingerVPN" <<'LAUNCH'
#!/bin/sh
DIR=$(cd "$(dirname "$0")/.." && pwd)
export WINGER_CORE="$DIR/core/sing-box"
exec "$DIR/jre/bin/java" -XstartOnFirstThread \
  -cp "$DIR/app/*:$DIR/app/lib/*" pro.wingerapp.vpn.MainKt "$@"
LAUNCH
chmod +x "$APP/Contents/MacOS/WingerVPN"

cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>WingerVPN</string>
    <key>CFBundleDisplayName</key><string>Winger VPN</string>
    <key>CFBundleIdentifier</key><string>pro.wingerapp.vpn</string>
    <key>CFBundleVersion</key><string>1.0.75</string>
    <key>CFBundleShortVersionString</key><string>1.0.75</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleExecutable</key><string>WingerVPN</string>
    <key>CFBundleIconFile</key><string>AppIcon</string>
    <key>LSMinimumSystemVersion</key><string>11.0</string>
    <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST

plutil -lint "$APP/Contents/Info.plist" >/dev/null

# 5. Подпись ad-hoc: без неё macOS ругается на «повреждённое» приложение.
codesign --force --deep --sign - "$APP" >/dev/null 2>&1 || true

# 6. DMG: приложение + ярлык Applications.
ln -s /Applications "$STAGE/Applications"
rm -f "$DIST/WingerVPN-1.0.75-intel.dmg"
hdiutil create -quiet -volname "WingerVPN" -srcfolder "$STAGE" \
  -ov -format UDZO "$DIST/WingerVPN-1.0.75-intel.dmg"

echo "готово: $DIST/WingerVPN-1.0.75-intel.dmg"
ls -lh "$DIST/WingerVPN-1.0.75-intel.dmg" | awk '{print "размер:", $5}'
