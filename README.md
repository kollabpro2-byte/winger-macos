# Winger VPN — десктоп

Тот же клиент, что и на Android: тот же интерфейс, три темы, вход по логину,
подписка и серверы. Написан на Kotlin + Compose Multiplatform, поэтому один и
тот же код собирается под Windows и macOS.

## Что уже работает

* Вход/регистрация, подписка, список серверов с группами и пингами.
* Три темы (тёмная/средняя/светлая), орёл, свечение, счётчики трафика.
* Экран «Аккаунт»: подписка, продление, устройства, Telegram, поддержка.
* «Настройки»: DNS, mux, IPv6, тема, частота трафика, очистка лога, сам лог.
* Туннель: запуск ядра `sing-box-lx` отдельным процессом, прослойка LTE4/LTE5,
  счётчики ↑/↓ через Clash API, автоперезапуск при падении ядра.

## Системный режим (tun на весь трафик)

В «Настройках → СИСТЕМНЫЙ РЕЖИМ → Установить» приложение один раз просит пароль
администратора и ставит привилегированный компонент. Дальше кнопка «включить»
работает мгновенно и без паролей — как в Happ/Incy:

| Платформа | Компонент | Управление |
|---|---|---|
| macOS | LaunchDaemon `pro.wingerapp.vpn.tunnel` + супервизор `winger-tunnel.sh` | файл `~/Library/Application Support/WingerVPN/tunnel.control` (`on`/`off`) |
| Windows | задача планировщика `WingerTunnel` с правами «наивысшие» | `schtasks /run|/end` |

Демон поднимает `sing-box` в tun-режиме от root/SYSTEM; приложение остаётся
обычным пользователем. Туннель переживает закрытие приложения, состояние
восстанавливается при следующем запуске (`TunnelController.resumeSaved`).

Удалить компонент — та же кнопка («Удалить»). Проверка установщика без root:
`tools/test_installer_mac.sh` (подменяет системные пути на /tmp и прогоняет
старт/стоп супервизора).

## Сборка под Windows

Портативный архив (работает сразу после распаковки, Java ставить не нужно):

```bash
# один раз: скачать ядро, JRE для Windows и wintun в dist/
winger-desktop/tools/fetch_windows_deps.sh
# собрать jar-ы и упаковать
winger-desktop/tools/package_windows.sh
# → dist/winger-vpn-windows-x64.zip
```

MSI ставится через CI: `.github/workflows/windows.yml` на `windows-latest`
делает `gradle packageMsi` (jpackage не умеет кросс-сборку с macOS).

## Сборка под macOS

Нужен JDK с `jpackage` (у JBR из Android Studio его нет):

```bash
curl -fsSL -o /tmp/jdk21.tar.gz \
  'https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse'
mkdir -p ~/.local/jdk21 && tar -xzf /tmp/jdk21.tar.gz -C ~/.local/jdk21 --strip-components=1

# ядро для macOS кладётся в resources/macos/core (Compose ждёт структуру <os>/…)
mkdir -p winger-desktop/resources/macos/core
cp dist/mac/sing-box winger-desktop/resources/macos/core/

export JAVA_HOME="$HOME/.local/jdk21/Contents/Home"
cd winger-desktop && gradle packageDmg
# → build/compose/binaries/main/dmg/WingerVPN-1.0.75.dmg
```

На macOS по умолчанию включается **прокси-режим** (без root): туннель поднимает
локальный прокси `127.0.0.1:2080`. Чтобы проверить настоящий VPN на весь трафик,
запустите приложение от root с флагом tun:

```bash
sudo env WINGER_MODE=tun /Applications/WingerVPN.app/Contents/MacOS/WingerVPN
```

## Запуск при разработке (macOS)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd winger-desktop
~/.local/gradle-8.7/bin/gradle run
```

Без окна — снимки экранов (проверка вёрстки):

```bash
WINGER_SNAPSHOT=/tmp/wd gradle run
```

Конфиг ядра для проверки совместимости:

```bash
WINGER_DUMP_CONFIG=/tmp/core.json gradle run
sing-box check -c /tmp/core.json
```

## Как устроен перенос с Android

| Android | Десктоп |
|---|---|
| `MainActivity` | `Main.kt` (окно, тема, заставка) |
| `WingerVpnService` + libbox | `TunnelController` + процесс `sing-box` |
| `Store` (SharedPreferences + Keystore) | `Store` (JSON + AES-GCM) |
| `VpnLog`, `Diag` | те же файлы, путь из `Platform` |
| `TrafficStats` | Clash API ядра (`/connections`) |
| `res/values/strings.xml`, `res/drawable` | `R.kt` + `resources/` (генерирует `tools/gen_resources.py`) |

UI-файлы (`ui/*.kt`) перенесены почти без изменений: Compose Multiplatform
использует те же пакеты `androidx.compose.*`. Отличия — платформенный слой
(`Platform.kt`, `ui/Res.kt`) и отсутствие `Context`.

## Где лежат данные

Windows: `%APPDATA%\WingerVPN`, macOS: `~/Library/Application Support/WingerVPN`.
Там настройки, зашифрованная сессия, лог, конфиг ядра и его журнал.
