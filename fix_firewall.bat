@echo off
echo Attempting to add Firewall Rule for Nexus Controller...
netsh advfirewall firewall delete rule name="NexusControllerTCP" >nul
netsh advfirewall firewall add rule name="NexusControllerTCP" dir=in action=allow protocol=TCP localport=6000
echo.
if %errorlevel% neq 0 (
    echo [ERROR] Failed to add rule. Please Right-Click this file and 'Run as Administrator'.
    pause
) else (
    echo [SUCCESS] Firewall rule added. You should be able to connect now!
    timeout /t 5
)
