[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 > $null

# Trading System 启动脚本
Write-Host "========== Trading System 启动脚本 ==========" -ForegroundColor Green

# 设置环境变量
$env:JAVA_HOME = "E:\Java"
$env:MAVEN_HOME = "E:\apache-maven-3.9.12-bin\apache-maven-3.9.12"
$env:PATH = "$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:PATH"

Write-Host "`nJava 版本:" -ForegroundColor Yellow
java -version
Write-Host "`nMaven 版本:" -ForegroundColor Yellow
mvn -version

# 检查是否在根目录
if (-not (Test-Path "pom.xml")) {
    Write-Host "错误：请在项目根目录执行此脚本" -ForegroundColor Red
    exit
}

Write-Host "`n[1/4] 编译打包..." -ForegroundColor Cyan
mvn clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "编译失败！" -ForegroundColor Red
    exit
}

$VERSION = "1.0.0-SNAPSHOT"

Write-Host "[2/4] 启动 trading-admin..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "java -jar trading-admin\target\trading-admin-$VERSION.jar"
Start-Sleep -Seconds 5

Write-Host "[3/4] 启动 trading-core..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "java --add-opens=java.base/sun.nio.ch=ALL-UNNAMED -jar trading-core\target\trading-core-$VERSION.jar"
Start-Sleep -Seconds 5

Write-Host "[4/4] 启动 trading-gateway..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "java -jar trading-gateway\target\trading-gateway-$VERSION.jar"

Write-Host "`n========== 启动完成 ==========" -ForegroundColor Green
Write-Host "trading-admin: http://localhost:8080" -ForegroundColor Yellow
Write-Host "trading-core: http://localhost:8081" -ForegroundColor Yellow
Write-Host "trading-gateway: port 9000" -ForegroundColor Yellow