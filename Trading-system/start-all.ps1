[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 > $null

# ========== 本地配置（请根据本机修改） ==========
# 若已配置系统环境变量 JAVA_HOME / MAVEN_HOME，可留空使用系统设置
$LOCAL_JAVA_HOME  = "E:\Java"
$LOCAL_MAVEN_HOME = "E:\apache-maven-3.9.12-bin\apache-maven-3.9.12"
# ================================================

# 项目根目录（脚本所在目录）
$ROOT = $PSScriptRoot
Set-Location $ROOT

# 设置环境变量（优先使用上面本地配置；留空则使用系统已配置的 JAVA_HOME/MAVEN_HOME）
if ($LOCAL_JAVA_HOME)  { $env:JAVA_HOME  = $LOCAL_JAVA_HOME }
if ($LOCAL_MAVEN_HOME) { $env:MAVEN_HOME = $LOCAL_MAVEN_HOME }
if ($env:JAVA_HOME)  { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }
if ($env:MAVEN_HOME) { $env:PATH = "$env:MAVEN_HOME\bin;$env:PATH" }

# Trading System 启动脚本
Write-Host "========== Trading System 启动脚本 ==========" -ForegroundColor Green
Write-Host "项目根目录: $ROOT" -ForegroundColor Gray

Write-Host "`nJava 版本:" -ForegroundColor Yellow
java -version
Write-Host "`nMaven 版本:" -ForegroundColor Yellow
mvn -version

# 检查是否在根目录
if (-not (Test-Path "$ROOT\pom.xml") -or -not (Test-Path "$ROOT\trading-admin")) 
{
    Write-Host "错误：请在 trading-system 项目根目录执行此脚本" -ForegroundColor Red
    exit 1
}

Write-Host "`n[1/4] 编译打包..." -ForegroundColor Cyan
mvn clean package -DskipTests -q -pl trading-common,trading-protocol,trading-admin,trading-core,trading-gateway -am
if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败！" -ForegroundColor Red
    exit 1
}

$VERSION = "1.0.0-SNAPSHOT"
$JAR_ADMIN  = "$ROOT\trading-admin\target\trading-admin-$VERSION.jar"
$JAR_CORE   = "$ROOT\trading-core\target\trading-core-$VERSION.jar"
$JAR_GATEWAY = "$ROOT\trading-gateway\target\trading-gateway-$VERSION.jar"

# 等待端口就绪（可选，用于后台启动时；当前为新窗口启动，仅做短等待）
function Wait-ForPort { param([int]$Port, [int]$MaxSeconds = 90)
    $n = 0
    Write-Host "  等待端口 $Port 就绪" -NoNewline
    while ($n -lt $MaxSeconds) {
        $conn = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
        if ($conn) { Write-Host " 就绪."; return $true }
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 1
        $n++
    }
    Write-Host " 超时."
    return $false
}

Write-Host "`n[2/4] 启动 trading-admin (端口 8080)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$ROOT'; java -jar '$JAR_ADMIN'"
Start-Sleep -Seconds 8
if (-not (Wait-ForPort -Port 8080 -MaxSeconds 90)) {
    Write-Host "trading-admin 启动超时，请查看新窗口输出或 MySQL 是否已启动、数据库 trading_admin 是否已创建" -ForegroundColor Red
    exit 1
}

Write-Host "`n[3/4] 启动 trading-core (端口 8081)..." -ForegroundColor Cyan
$JAVA_OPTS_CORE = "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$ROOT'; java $JAVA_OPTS_CORE -jar '$JAR_CORE'"
Start-Sleep -Seconds 5
if (-not (Wait-ForPort -Port 8081 -MaxSeconds 20)) {
    Write-Host "trading-core 启动超时，请查看新窗口输出" -ForegroundColor Red
    exit 1
}

Write-Host "`n[4/4] 启动 trading-gateway (端口 9000)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$ROOT'; java -jar '$JAR_GATEWAY'"
Start-Sleep -Seconds 5
if (-not (Wait-ForPort -Port 9000 -MaxSeconds 30)) {
    Write-Host "trading-gateway 启动超时，请查看新窗口输出" -ForegroundColor Yellow
}

Write-Host "`n========== 全部服务已启动 ==========" -ForegroundColor Green
Write-Host "  trading-admin:  http://localhost:8080" -ForegroundColor Yellow
Write-Host "  trading-core:   http://localhost:8081" -ForegroundColor Yellow
Write-Host "  trading-gateway: TCP localhost:9000" -ForegroundColor Yellow
Write-Host "  关闭各服务请关闭对应 PowerShell 窗口，或运行 stop-all.bat" -ForegroundColor Gray
