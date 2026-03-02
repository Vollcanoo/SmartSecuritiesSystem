#!/bin/bash
# 测试订单历史查询和管理界面

echo "===== 2.2.5 数据分析-订单历史测试 ====="
echo ""

echo "--- T20: 查询所有订单历史 ---"
curl -s "http://localhost:8080/api/order-history?page=0&size=20" | python3 -c '
import sys,json
d=json.load(sys.stdin)
if d.get("code")==0:
    data=d["data"]
    content=data.get("content",[])
    print(f"总记录数: {data.get(\"totalElements\",0)}, 本页: {len(content)}")
    for o in content:
        print(f"  {o[\"clOrderId\"]} {o[\"side\"]} {o.get(\"securityId\",\"?\")} qty={o.get(\"orderQty\",0)} filled={o.get(\"filledQty\",0)} status={o.get(\"status\",\"?\")}")
else:
    print(d)
'
echo ""

echo "--- T21: 按股东号查询 ---"
curl -s "http://localhost:8080/api/order-history?shareholderId=SH00010002&page=0&size=10" | python3 -c '
import sys,json
d=json.load(sys.stdin)
content=d["data"].get("content",[])
print(f"用户B订单数: {len(content)}")
for o in content:
    print(f"  {o[\"clOrderId\"]} {o[\"side\"]} filled={o.get(\"filledQty\",0)} status={o.get(\"status\",\"?\")}")
'
echo ""

echo "--- T22: 按状态查询(FILLED) ---"
curl -s "http://localhost:8080/api/order-history?status=FILLED&page=0&size=10" | python3 -c '
import sys,json
d=json.load(sys.stdin)
content=d["data"].get("content",[])
print(f"已成交订单数: {len(content)}")
for o in content:
    print(f"  {o[\"clOrderId\"]} filled={o.get(\"filledQty\",0)} status={o[\"status\"]}")
'
echo ""

echo "===== 2.2.4 管理界面测试 ====="
echo ""
echo "--- T23: 检查前端页面可访问性 ---"
for page in index.html users.html trading.html orders.html open-orders.html; do
    code=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:8000/$page")
    if [ "$code" = "200" ]; then
        echo "  ✅ $page → $code"
    else
        echo "  ❌ $page → $code"
    fi
done
echo ""

echo "--- T24: Admin 服务 API 检查 ---"
echo "  用户列表: $(curl -s http://localhost:8080/api/users | python3 -c 'import sys,json;d=json.load(sys.stdin);print(f"共{len(d[\"data\"])}用户")')"
echo "  用户余额: $(curl -s http://localhost:8080/api/users/10001/balance | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d["data"])')"
echo ""

echo "--- T25: Core 健康检查 ---"
echo "  actuator: $(curl -s http://localhost:8081/actuator/health)"
echo ""

echo "--- T26: Gateway TCP 连通性 ---"
RESP=$(echo '{"market":"XSHG","securityId":"601988","side":"B","qty":10,"price":5.00,"shareholderId":"SH00010002"}' | nc -w 3 localhost 9000)
echo "  Gateway响应: $RESP"
