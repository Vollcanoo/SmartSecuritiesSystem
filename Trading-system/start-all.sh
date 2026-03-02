#!/usr/bin/env bash
#
# 一键启动 trading-system 全部服务（Admin / Core / Gateway）
# 使用方式：在项目根目录执行 ./start-all.sh
# 停止方式：./stop-all.sh
#

set -e
cd "$(dirname "$0")"
ROOT="$(pwd)"
PID_FILE="$ROOT/.trading-system.pids"
VERSION="1.0.0-SNAPSHOT"

# 检查是否在项目根目录
if [ ! -f "$ROOT/pom.xml" ] || [ ! -d "$ROOT/trading-admin" ]; then
  echo "错误：请在 trading-system 项目根目录下执行 ./start-all.sh"
  exit 1
fi

# 本脚本强制使用 JDK 17（exchange-core 等依赖要求）
is_java17() {
  local j="$1"
  [ -x "$j" ] && "$j" -version 2>&1 | grep -q 'version "17'
}
JAVA17_HOME=""
if [ -n "$JAVA_HOME" ] && is_java17 "$JAVA_HOME/bin/java"; then
  JAVA17_HOME="$JAVA_HOME"
elif [ -x "/usr/local/opt/openjdk@17/bin/java" ] && is_java17 "/usr/local/opt/openjdk@17/bin/java"; then
  JAVA17_HOME="/usr/local/opt/openjdk@17"
elif [ -x "/opt/homebrew/opt/openjdk@17/bin/java" ] && is_java17 "/opt/homebrew/opt/openjdk@17/bin/java"; then
  JAVA17_HOME="/opt/homebrew/opt/openjdk@17"
fi
if [ -z "$JAVA17_HOME" ]; then
  echo "错误：本项目要求 JDK 17。当前 java 版本："
  java -version 2>&1 || true
  echo ""
  echo "请安装 JDK 17 后任选其一再执行本脚本："
  echo "  brew install openjdk@17"
  echo "  export JAVA_HOME=/usr/local/opt/openjdk@17   # Intel Mac"
  echo "  export JAVA_HOME=/opt/homebrew/opt/openjdk@17 # Apple Silicon"
  echo "  ./start-all.sh"
  exit 1
fi
export JAVA_HOME="$JAVA17_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
echo "使用 JDK 17: $JAVA_HOME"
echo ""

# 若已有 PID 文件，先尝试停止旧进程
if [ -f "$PID_FILE" ]; then
  echo "检测到已有 .trading-system.pids，正在停止旧进程..."
  ( "$ROOT/stop-all.sh" 2>/dev/null ) || true
  rm -f "$PID_FILE"
fi

# 检测端口是否已监听
port_ready() {
  local port=$1
  nc -z 127.0.0.1 "$port" 2>/dev/null
}

# 等待端口可用（最多等待秒数）
wait_for_port() {
  local port=$1
  local max=${2:-90}
  local n=0
  echo -n "  等待端口 $port 就绪"
  while [ $n -lt $max ]; do
    if port_ready "$port"; then
      echo " 就绪."
      return 0
    fi
    echo -n "."
    sleep 1
    n=$((n + 1))
  done
  echo " 超时."
  return 1
}

# 1. 打包（clean 确保使用最新代码，跳过测试）
echo "========== 1/4 编译打包 =========="
if ! mvn clean package -DskipTests -q -pl trading-common,trading-protocol,trading-admin,trading-core,trading-gateway -am 2>/dev/null; then
  echo "打包失败。若因缺少 exchange-core 导致 trading-core 编译失败，可先只打包其他模块："
  echo "  mvn clean package -DskipTests -q -pl trading-common,trading-protocol,trading-admin,trading-gateway -am"
  echo "然后修改本脚本，注释掉 trading-core 与 trading-gateway 的启动部分后再执行。"
  exit 1
fi

# 2. 启动 trading-admin（使用 MySQL，请先启动 MySQL 并创建数据库 trading_admin）
echo "========== 2/4 启动 trading-admin (端口 8080, MySQL) =========="
java -jar "$ROOT/trading-admin/target/trading-admin-$VERSION.jar" > "$ROOT/logs-admin.log" 2>&1 &
echo $! >> "$PID_FILE"
wait_for_port 8080 90 || {
  echo "trading-admin 启动超时，请查看日志最后几行："
  echo "---"
  tail -n 40 "$ROOT/logs-admin.log" 2>/dev/null || true
  echo "---"
  echo "完整日志: $ROOT/logs-admin.log （请确认 MySQL 已启动且已创建数据库 trading_admin）"
  exit 1
}

# 3. 启动 trading-core（需 JVM --add-opens 以兼容 exchange-core 依赖）
echo "========== 3/4 启动 trading-core (端口 8081) =========="
JAVA_OPTS_CORE="--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED"
java $JAVA_OPTS_CORE -jar "$ROOT/trading-core/target/trading-core-$VERSION.jar" > "$ROOT/logs-core.log" 2>&1 &
echo $! >> "$PID_FILE"
wait_for_port 8081 20 || {
  echo "trading-core 启动超时，请查看日志最后几行："
  echo "---"
  tail -n 50 "$ROOT/logs-core.log" 2>/dev/null || true
  echo "---"
  echo "完整日志: $ROOT/logs-core.log"
  exit 1
}

# 4. 启动 trading-gateway
echo "========== 4/4 启动 trading-gateway (端口 9000) =========="
java -jar "$ROOT/trading-gateway/target/trading-gateway-$VERSION.jar" > "$ROOT/logs-gateway.log" 2>&1 &
echo $! >> "$PID_FILE"
wait_for_port 9000 30 || { echo "trading-gateway 启动超时"; exit 1; }

echo ""
echo "========== 全部服务已启动 =========="
echo "  trading-admin:  http://localhost:8080"
echo "  trading-core:   http://localhost:8081"
echo "  trading-gateway: TCP localhost:9000"
echo "  进程 PID 已写入 $PID_FILE"
echo "  停止全部服务请执行: ./stop-all.sh"
echo "  日志: logs-admin.log, logs-core.log, logs-gateway.log"
echo ""
