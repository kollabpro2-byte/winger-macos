@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem Режим без прав администратора: ядро поднимает локальный прокси
rem (127.0.0.1:2080), а не виртуальный адаптер. Системный трафик при этом
rem не заворачивается — режим годится для проверки и для клиентов, которые
rem умеют работать через прокси.
set "WINGER_CORE=%~dp0core\sing-box.exe"
set "WINGER_MODE=proxy"
start "" "%~dp0jre\bin\javaw.exe" -cp "%~dp0app\app.jar;%~dp0app\lib\*" pro.wingerapp.vpn.MainKt
