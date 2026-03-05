#!/bin/bash
# 启动前端 HTTP 服务器

cd "$(dirname "$0")"

echo "正在启动前端 HTTP 服务器..."
echo "访问地址: http://localhost:8000"
echo ""
echo "可用页面:"
echo "  - http://localhost:8000/index.html (系统概览)"
echo "  - http://localhost:8000/users.html (用户管理)"
echo "  - http://localhost:8000/trading.html (交易下单)"
echo "  - http://localhost:8000/orders.html (订单历史)"
echo "  - http://localhost:8000/open-orders.html (当前挂单)"
echo "  - http://localhost:8000/test.html (测试页面)"
echo ""
echo "按 Ctrl+C 停止服务器"
echo ""

# 检查 Python 3
if command -v python3 &> /dev/null; then
    python3 -m http.server 8000
elif command -v python &> /dev/null; then
    python -m SimpleHTTPServer 8000
else
    echo "错误: 未找到 Python，请安装 Python 3"
    exit 1
fi
