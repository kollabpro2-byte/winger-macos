#!/usr/bin/env bash
# Проверка установщика macOS без root: системные пути подменяются на /tmp,
# launchctl/chown убираются. Проверяем, что скрипт создаёт файлы и валидный plist.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SANDBOX="/tmp/winger-installer-test"
rm -rf "$SANDBOX"
mkdir -p "$SANDBOX/launchd" "$SANDBOX/data"

SRC="$ROOT/winger-desktop/src/main/resources/installer/install-mac.sh"
DST="$SANDBOX/install.sh"

sed -e "s#@DATA@#$SANDBOX/data#" \
    -e "s#@CORE@#$ROOT/dist/mac/sing-box#" \
    -e "s#/Library/Application Support/WingerVPN#$SANDBOX/lib#g" \
    -e "s#/Library/LaunchDaemons/pro.wingerapp.vpn.tunnel.plist#$SANDBOX/launchd/tunnel.plist#g" \
    -e "s#/var/log/winger-tunnel.log#$SANDBOX/daemon.log#" \
    -e "/^chown /d" \
    -e "/^launchctl /d" \
    "$SRC" > "$DST"

sh -n "$DST"
sh "$DST"

echo "--- созданные файлы ---"
ls -1 "$SANDBOX/lib" "$SANDBOX/launchd"
echo "--- plist ---"
plutil -lint "$SANDBOX/launchd/tunnel.plist"
echo "--- контрольная строка ---"
cat "$SANDBOX/data/tunnel.control"; echo
echo "--- супервизор: первые строки ---"
sed -n '1,12p' "$SANDBOX/lib/winger-tunnel.sh"
echo "--- контрольное состояние: выключено ---"
pgrep -f "$SANDBOX/lib/sing-box run -c" >/dev/null && echo "ОШИБКА: ядро запущено" || echo "ок: ядро не запущено"

echo "--- включаем: пишем on и ждём 2 с ---"
printf 'on' > "$SANDBOX/data/tunnel.control"
cp "$ROOT/dist/mac/sing-box" "$SANDBOX/lib/sing-box" 2>/dev/null || true
# Конфиг без rule-set: ядро не ходит в сеть и остаётся жить, чтобы проверить
# именно управление (старт/стоп), а не скачивание правил.
cat > "$SANDBOX/data/core.json" <<'JSON'
{
  "log": {"level": "warn", "output": "/dev/null"},
  "inbounds": [{"type": "mixed", "tag": "in", "listen": "127.0.0.1", "listen_port": 12080}],
  "outbounds": [{"type": "direct", "tag": "direct"}],
  "route": {"rules": [{"action": "sniff"}]},
  "experimental": {"clash_api": {"external_controller": "127.0.0.1:19090"}}
}
JSON
"$SANDBOX/lib/winger-tunnel.sh" & SUP=$!
sleep 3
pgrep -f "$SANDBOX/lib/sing-box run -c" >/dev/null && echo "ок: супервизор поднял ядро" || echo "ядро не поднялось"
printf 'off' > "$SANDBOX/data/tunnel.control"
sleep 2
pgrep -f "$SANDBOX/lib/sing-box run -c" >/dev/null && echo "ОШИБКА: ядро не погасло" || echo "ок: ядро погашено"
kill $SUP 2>/dev/null || true
