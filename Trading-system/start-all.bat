@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"
REM 要求：JDK 17；MySQL 已启动并已创建数据库 trading_admin

echo ========== 一键启动 trading-system ==========

if not exist "pom.xml" (
  echo 错误：请在项目根目录下执行 start-all.bat
  pause
  exit /b 1
)

echo.
echo [1/4] 编译打包...
call mvn package -DskipTests -q -pl trading-common,trading-protocol,trading-admin,trading-core,trading-gateway -am
if errorlevel 1 (
  echo 打包失败。
  pause
  exit /b 1
)

set VERSION=1.0.0-SNAPSHOT
echo.
echo [2/4] 启动 trading-admin (端口 8080, MySQL)...
start "trading-admin" java -jar "trading-admin\target\trading-admin-%VERSION%.jar"
timeout /t 15 /nobreak >nul

echo [3/4] 启动 trading-core (端口 8081)...
start "trading-core" java --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -jar "trading-core\target\trading-core-%VERSION%.jar"
timeout /t 25 /nobreak >nul

echo [4/4] 启动 trading-gateway (端口 9000)...
start "trading-gateway" java -jar "trading-gateway\target\trading-gateway-%VERSION%.jar"
timeout /t 10 /nobreak >nul

echo.
echo ========== 全部服务已在新窗口中启动 ==========
echo   trading-admin:  http://localhost:8080
echo   trading-core:   http://localhost:8081
echo   trading-gateway: TCP localhost:9000
echo.
echo 关闭对应窗口即可停止该服务；或运行 stop-all.bat 结束所有 Java 进程。
echo.
pause
