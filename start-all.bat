@echo off
setlocal
cd /d "%~dp0"

echo ==========================================
echo    Trading System Startup Script
echo ==========================================

:: Check if pom.xml exists
if not exist "pom.xml" (
    echo [ERROR] Please run this script in the project root directory!
    pause
    exit /b 1
)

:: 1. Maven Build
echo [1/5] Building modules (excluding exchange-core)...
call mvn clean package -DskipTests -pl trading-common,trading-protocol,trading-admin,trading-core,trading-gateway -am
if errorlevel 1 (
    echo [ERROR] Maven build failed!
    pause
    exit /b 1
)

set VERSION=1.0.0-SNAPSHOT

:: 2. Start Admin (Port 8080)
echo [2/5] Starting trading-admin...
start "trading-admin" java -jar "trading-admin\target\trading-admin-%VERSION%.jar"
timeout /t 15 /nobreak >nul

:: 3. Start Core (Port 8081)
echo [3/5] Starting trading-core...
set CORE_OPTS=--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED
start "trading-core" java %CORE_OPTS% -jar "trading-core\target\trading-core-%VERSION%.jar"
timeout /t 25 /nobreak >nul

:: 4. Start Gateway (Port 9000)
echo [4/5] Starting trading-gateway...
start "trading-gateway" java -jar "trading-gateway\target\trading-gateway-%VERSION%.jar"
timeout /t 10 /nobreak >nul

:: 5. Start Frontend Server (Port 8000)
echo [5/5] Starting frontend server via Python...
:: Try 'python' first, then 'python3' if python fails
start "trading-frontend" /D "trading-frontend" cmd /c "python -m http.server 8000 || python3 -m http.server 8000"
timeout /t 3 /nobreak >nul

echo.
echo ==========================================
echo    All services started!
echo    Admin:    http://localhost:8080
echo    Core:     http://localhost:8081
echo    Gateway:  TCP 9000
echo    Frontend: http://localhost:8000
echo ==========================================
echo.
pause