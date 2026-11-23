@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem VPN на весь трафик (tun) требует прав администратора: без них sing-box не
rem создаст виртуальный адаптер. Перезапускаем себя через UAC.
net session >nul 2>&1
if errorlevel 1 (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

set "WINGER_CORE=%~dp0core\sing-box.exe"
start "" "%~dp0jre\bin\javaw.exe" -cp "%~dp0lib\*" pro.wingerapp.vpn.MainKt
