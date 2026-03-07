@echo off
chcp 65001 >nul
setlocal EnableExtensions
cd /d "%~dp0"

set "VERSION=1.0.0-SNAPSHOT"
set "BUILD_MODULES=trading-common,trading-protocol,trading-admin,trading-core,trading-gateway"
set "ADMIN_JAR=trading-admin\target\trading-admin-%VERSION%.jar"
set "CORE_JAR=trading-core\target\trading-core-%VERSION%.jar"
set "GATEWAY_JAR=trading-gateway\target\trading-gateway-%VERSION%.jar"
set "JAVA_EXE=java"
set "MAVEN_CMD=mvn"

if exist "mvnw.cmd" (
  set "MAVEN_CMD=mvnw.cmd"
)

echo ========== start-all: build then start ==========

if not exist "pom.xml" (
  echo [ERROR] Please run this script in the trading-system root directory.
  pause
  exit /b 1
)

echo.
echo [1/5] Building project...
echo Command: %MAVEN_CMD% clean package -DskipTests -pl %BUILD_MODULES% -am
call "%MAVEN_CMD%" clean package -DskipTests -pl %BUILD_MODULES% -am
if errorlevel 1 (
  echo [ERROR] Build failed.
  pause
  exit /b 1
)

if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)

if not exist "%JAVA_EXE%" (
  where "%JAVA_EXE%" >nul 2>nul
  if errorlevel 1 (
    if exist "%USERPROFILE%\Desktop\jdk-17.0.18\bin\java.exe" set "JAVA_EXE=%USERPROFILE%\Desktop\jdk-17.0.18\bin\java.exe"
    if exist "%USERPROFILE%\Desktop\jdk-17\bin\java.exe" set "JAVA_EXE=%USERPROFILE%\Desktop\jdk-17\bin\java.exe"
  )
)

if not exist "%JAVA_EXE%" (
  where "%JAVA_EXE%" >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] java not found.
    echo Please set JAVA_HOME or add JDK17 bin to PATH.
    pause
    exit /b 1
  )
)

if not exist "%ADMIN_JAR%" (
  echo [ERROR] Missing jar after build: %ADMIN_JAR%
  pause
  exit /b 1
)

if not exist "%CORE_JAR%" (
  echo [ERROR] Missing jar after build: %CORE_JAR%
  pause
  exit /b 1
)

if not exist "%GATEWAY_JAR%" (
  echo [ERROR] Missing jar after build: %GATEWAY_JAR%
  pause
  exit /b 1
)

echo Using Java: %JAVA_EXE%

echo.
echo [2/5] Starting trading-admin (8080)...
start "trading-admin" "%JAVA_EXE%" -jar "%ADMIN_JAR%"
timeout /t 12 /nobreak >nul

echo [3/5] Starting trading-core (8081)...
start "trading-core" "%JAVA_EXE%" --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -jar "%CORE_JAR%"
timeout /t 18 /nobreak >nul

echo [4/5] Starting trading-gateway (8082/9000)...
start "trading-gateway" "%JAVA_EXE%" -jar "%GATEWAY_JAR%"
timeout /t 8 /nobreak >nul
echo [5/5] Starting trading-frontend (8000)...
start "trading-frontend" cmd /k "cd trading-frontend && py -3 -m http.server 8000"
timeout /t 3 /nobreak >nul
echo.
echo ========== services started ==========
echo   trading-frontend: http://localhost:8000
echo   trading-admin:   http://localhost:8080
echo   trading-core:    http://localhost:8081
echo   trading-gateway: http://localhost:8082
echo   trading-gateway: tcp://localhost:9000
echo.
echo Use stop-all.bat to stop backend service windows.
pause
