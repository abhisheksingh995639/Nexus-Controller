@echo off
:: Check for Administrator privileges
NET SESSION >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo Requesting Administrator privileges...
    powershell -Command "Start-Process '%~dp0RunServer.bat' -Verb RunAs"
    exit /b
)

cd /d "%~dp0"
echo Starting Controller Server...
python gui_app.py
pause
