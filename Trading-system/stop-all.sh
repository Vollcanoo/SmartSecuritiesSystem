#!/usr/bin/env bash
#
# 一键停止由 start-all.sh 启动的 trading-system 全部服务
# 使用方式：在项目根目录执行 ./stop-all.sh
#

cd "$(dirname "$0")"
ROOT="$(pwd)"
PID_FILE="$ROOT/.trading-system.pids"

if [ ! -f "$PID_FILE" ]; then
  echo "未找到 .trading-system.pids，没有通过 start-all.sh 启动的进程需要停止。"
  exit 0
fi

echo "正在停止 trading-system 服务..."
while read -r pid; do
  [ -z "$pid" ] && continue
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
    echo "  已发送停止信号 PID=$pid"
  fi
done < "$PID_FILE"

# 等待进程退出
sleep 2
while read -r pid; do
  [ -z "$pid" ] && continue
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null || true
    echo "  已强制结束 PID=$pid"
  fi
done < "$PID_FILE"

rm -f "$PID_FILE"
echo "已停止全部服务并清除 .trading-system.pids"
