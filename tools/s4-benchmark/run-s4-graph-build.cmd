@echo off
chcp 65001 >nul
cd /d "%~dp0..\.."
set "PS=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS%" set "PS=powershell"
"%PS%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-s4-graph-build.ps1"
echo.
echo Press any key to close this window.
pause >nul
