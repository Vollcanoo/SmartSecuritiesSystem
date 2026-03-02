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

# 本脚本要求 JDK 17 或更高版本（编译目标为 17，高版本 JDK 向下兼容）
get_java_major_version() {
  local j="$1"
  if [ ! -x "$j" ]; then echo 0; return; fi
  local ver
  ver=$("$j" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
  echo "${ver:-0}"
}

is_java17_plus() {
  local j="$1"
  local v
  v=$(get_java_major_version "$j")
  [ "$v" -ge 17 ] 2>/dev/null
}

JAVA_OK_HOME=""
# 优先使用用户设置的 JAVA_HOME
if [ -n "$JAVA_HOME" ] && is_java17_plus "$JAVA_HOME/bin/java"; then
  JAVA_OK_HOME="$JAVA_HOME"
# 其次检查 Homebrew JDK 17
elif [ -x "/opt/homebrew/opt/openjdk@17/bin/java" ] && is_java17_plus "/opt/homebrew/opt/openjdk@17/bin/java"; then
  JAVA_OK_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
elif [ -x "/usr/local/opt/openjdk@17/bin/java" ] && is_java17_plus "/usr/local/opt/openjdk@17/bin/java"; then
  JAVA_OK_HOME="/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
# 检查 Homebrew JDK 21
elif [ -x "/opt/homebrew/opt/openjdk@21/bin/java" ] && is_java17_plus "/opt/homebrew/opt/openjdk@21/bin/java"; then
  JAVA_OK_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
elif [ -x "/usr/local/opt/openjdk@21/bin/java" ] && is_java17_plus "/usr/local/opt/openjdk@21/bin/java"; then
  JAVA_OK_HOME="/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
# 最后检查系统默认 java
elif command -v java >/dev/null 2>&1 && is_java17_plus "$(command -v java)"; then
  # 通过 /usr/libexec/java_home 获取 JAVA_HOME（macOS）
  if [ -x /usr/libexec/java_home ]; then
    JAVA_OK_HOME="$(/usr/libexec/java_home 2>/dev/null)" || true
  fi
  # 如果仍为空，尝试从 java 路径推断
  if [ -z "$JAVA_OK_HOME" ]; then
    local real_java
    real_java=$(readlink -f "$(command -v java)" 2>/dev/null || realpath "$(command -v java)" 2>/dev/null)
    if [ -n "$real_java" ]; then
      JAVA_OK_HOME="$(dirname "$(dirname "$real_java")")"
    fi
  fi
fi

if [ -z "$JAVA_OK_HOME" ]; then
  echo "错误：本项目要求 JDK 17 或更高版本。当前 java 版本："
  java -version 2>&1 || true
  echo ""
  echo "请安装 JDK 17+ 后任选其一再执行本脚本："
  echo "  brew install openjdk@17"
  echo "  export JAVA_HOME=/usr/local/opt/openjdk@17   # Intel Mac"
  echo "  export JAVA_HOME=/opt/homebrew/opt/openjdk@17 # Apple Silicon"
  echo "  ./start-all.sh"
  exit 1
fi
export JAVA_HOME="$JAVA_OK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
JAVA_VER=$(get_java_major_version "$JAVA_HOME/bin/java")
echo "使用 JDK $JAVA_VER: $JAVA_HOME"
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

# 检测 MySQL 是否可用，决定 Admin 使用哪个数据库
ADMIN_PROFILE=""
if command -v mysql >/dev/null 2>&1 && mysql -u root -e "SELECT 1" >/dev/null 2>&1; then
  echo "检测到 MySQL 可用，Admin 将使用 MySQL 数据库"
else
  echo "未检测到 MySQL 或无法连接，Admin 将使用 H2 内存数据库（--spring.profiles.active=h2）"
  ADMIN_PROFILE="--spring.profiles.active=h2"
fi
echo ""

# 1. 打包（clean 确保使用最新代码，跳过测试）
echo "========== 1/4 编译打包 =========="
if ! mvn clean package -DskipTests -q -pl trading-common,trading-protocol,trading-admin,trading-core,trading-gateway -am 2>/dev/null; then
  echo "打包失败。若因缺少 exchange-core 导致 trading-core 编译失败，可先只打包其他模块："
  echo "  mvn clean package -DskipTests -q -pl trading-common,trading-protocol,trading-admin,trading-gateway -am"
  echo "然后修改本脚本，注释掉 trading-core 与 trading-gateway 的启动部分后再执行。"
  exit 1
fi

# 2. 启动 trading-admin（使用 MySQL，请先启动 MySQL 并创建数据库 trading_admin）
if [ -n "$ADMIN_PROFILE" ]; then
  echo "========== 2/4 启动 trading-admin (端口 8080, H2 内存数据库) =========="
else
  echo "========== 2/4 启动 trading-admin (端口 8080, MySQL) =========="
fi
java -jar "$ROOT/trading-admin/target/trading-admin-$VERSION.jar" $ADMIN_PROFILE > "$ROOT/logs-admin.log" 2>&1 &
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
JAVA_OPTS_CORE="--add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED --add-opens=java.base/sun.security.ssl=ALL-UNNAMED --add-opens=java.base/sun.misc=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang.invoke=ALL-UNNAMED --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED --add-exports=java.base/sun.misc=ALL-UNNAMED --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED"
java $JAVA_OPTS_CORE -jar "$ROOT/trading-core/target/trading-core-$VERSION.jar" > "$ROOT/logs-core.log" 2>&1 &
echo $! >> "$PID_FILE"
wait_for_port 8081 60 || {
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

# 5. 启动 trading-frontend
echo "========== 5/5 启动 trading-frontend (端口 8000) =========="
# 尝试释放端口 8000
lsof -t -i:8000 | xargs kill -9 2>/dev/null || true

cd "$ROOT/trading-frontend"
if command -v python3 &> /dev/null; then
    nohup python3 -m http.server 8000 > "$ROOT/logs-frontend.log" 2>&1 &
    FRONT_PID=$!
elif command -v python &> /dev/null; then
    nohup python -m SimpleHTTPServer 8000 > "$ROOT/logs-frontend.log" 2>&1 &
    FRONT_PID=$!
else
    echo "未找到 Python，跳过前端启动"
    FRONT_PID=""
fi

if [ -n "$FRONT_PID" ]; then
    # python PID
    echo $FRONT_PID >> "$PID_FILE"
    # Wait for port 8000 to be open using wait_for_port (which uses nc -z)
    echo "等待 trading-frontend端口 8000..."
    # Local wait loop because wait_for_port might be strict about exit on failure
    for i in {1..10}; do
        if nc -z 127.0.0.1 8000 >/dev/null 2>&1; then
             break
        fi
        sleep 1
    done
fi
cd "$ROOT"

echo ""
echo "========== 全部服务已启动 =========="
echo "  [前端页面] http://localhost:8000 （请访问此地址）"
echo "  [后台管理] http://localhost:8080"
echo "  [核心交易] http://localhost:8081"
echo "  trading-core:   http://localhost:8081"
echo "  trading-gateway: TCP localhost:9000"
echo "  进程 PID 已写入 $PID_FILE"
echo "  停止全部服务请执行: ./stop-all.sh"
echo "  日志: logs-admin.log, logs-core.log, logs-gateway.log"
echo ""
