[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 > $null

Write-Host "========== Trading System 环境变量配置 ==========" -ForegroundColor Green

# ====== 根据你本机实际安装路径修改这里 ======
# Java 安装目录（JDK 根目录）
$LOCAL_JAVA_HOME  = "E:\Java"

# Maven 安装目录（Maven 根目录，包含 bin 目录）
$LOCAL_MAVEN_HOME = "E:\apache-maven-3.9.12-bin\apache-maven-3.9.12"
# ==========================================

if (-not (Test-Path $LOCAL_JAVA_HOME)) {
    Write-Host "警告：JAVA_HOME 路径不存在：$LOCAL_JAVA_HOME" -ForegroundColor Yellow
} else {
    $env:JAVA_HOME = $LOCAL_JAVA_HOME
    Write-Host "已设置 JAVA_HOME = $env:JAVA_HOME" -ForegroundColor Cyan
}

if (-not (Test-Path $LOCAL_MAVEN_HOME)) {
    Write-Host "警告：MAVEN_HOME 路径不存在：$LOCAL_MAVEN_HOME" -ForegroundColor Yellow
} else {
    $env:MAVEN_HOME = $LOCAL_MAVEN_HOME
    Write-Host "已设置 MAVEN_HOME = $env:MAVEN_HOME" -ForegroundColor Cyan
}

if ($env:JAVA_HOME) {
    $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
}
if ($env:MAVEN_HOME) {
    $env:PATH = "$env:MAVEN_HOME\bin;$env:PATH"
}

Write-Host "`n当前 Java 版本：" -ForegroundColor Yellow
java -version

Write-Host "`n当前 Maven 版本：" -ForegroundColor Yellow
mvn -version

Write-Host "`n说明：" -ForegroundColor Green
Write-Host "1. 本脚本只对当前 PowerShell 会话生效，关闭窗口后失效。" -ForegroundColor Gray
Write-Host "2. 若路径与实际安装位置不一致，请编辑 set-env.ps1 顶部的 LOCAL_JAVA_HOME / LOCAL_MAVEN_HOME。" -ForegroundColor Gray
Write-Host "3. 之后可以在同一窗口中执行 .\start-all.ps1 启动整个系统。" -ForegroundColor Gray

