@echo off
echo Stopping Trading System services...
echo.

:: Kill processes by window title
taskkill /F /FI "WINDOWTITLE eq trading-admin*" /T 2>nul
taskkill /F /FI "WINDOWTITLE eq trading-core*" /T 2>nul
taskkill /F /FI "WINDOWTITLE eq trading-gateway*" /T 2>nul
taskkill /F /FI "WINDOWTITLE eq trading-frontend*" /T 2>nul

echo.
echo All services have been stopped.
pause