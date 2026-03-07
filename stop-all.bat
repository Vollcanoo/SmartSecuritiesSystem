@echo off
chcp 65001 >nul
echo 正在停止 trading-system 相关 Java 进程...
echo.
taskkill /FI "WINDOWTITLE eq trading-admin*" /F 2>nul
taskkill /FI "WINDOWTITLE eq trading-core*" /F 2>nul
taskkill /FI "WINDOWTITLE eq trading-gateway*" /F 2>nul
echo.
echo 若仍有残留，可手动关闭标题含 trading-admin / trading-core / trading-gateway 的窗口，
echo 或执行: taskkill /IM java.exe /F  （会结束本机所有 Java 进程，慎用）
pause
