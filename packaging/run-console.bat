@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem Запуск с консолью: сюда печатаются ошибки Java и ядра. Нужен, если окно
rem закрывается сразу или пишет «ядро не запустилось».
net session >nul 2>&1
if errorlevel 1 (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

set "WINGER_CORE=%~dp0core\sing-box.exe"
"%~dp0jre\bin\java.exe" -cp "%~dp0lib\*" pro.wingerapp.vpn.MainKt
pause
