#!/usr/bin/env bash
#
# 系统功能测试脚本
# 用于测试 Trading System 的核心功能
#

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 服务地址
ADMIN_URL="http://localhost:8080"
CORE_URL="http://localhost:8081"
GATEWAY_URL="http://localhost:8082"

# 测试计数
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 打印函数
print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_test() {
    echo -e "${YELLOW}测试 $1: $2${NC}"
}

print_success() {
    echo -e "${GREEN}✓ 成功: $1${NC}"
    ((PASSED_TESTS++))
    ((TOTAL_TESTS++))
}

print_failure() {
    echo -e "${RED}✗ 失败: $1${NC}"
    echo -e "${RED}  原因: $2${NC}"
    ((FAILED_TESTS++))
    ((TOTAL_TESTS++))
}

print_info() {
    echo -e "${BLUE}ℹ 信息: $1${NC}"
}

# 检查服务健康状态
check_service_health() {
    local name=$1
    local url=$2
    
    print_test "0" "检查 $name 健康状态"
    
    if curl -s "${url}/actuator/health" | grep -q "UP"; then
        print_success "$name 服务运行正常"
        return 0
    else
        print_failure "$name 服务不可用" "无法访问 ${url}/actuator/health"
        return 1
    fi
}

# 测试用户管理
test_user_management() {
    print_header "测试用户管理功能"
    
    # 测试1: 创建用户
    print_test "1.1" "创建用户"
    local response=$(curl -s -X POST ${ADMIN_URL}/api/users \
        -H "Content-Type: application/json" \
        -d '{"username":"测试用户1"}')
    
    if echo "$response" | grep -q "shareholderId"; then
        local uid=$(echo "$response" | grep -o '"uid":[0-9]*' | cut -d':' -f2)
        local shareholderId=$(echo "$response" | grep -o '"shareholderId":"[^"]*"' | cut -d'"' -f4)
        print_success "用户创建成功 (UID: $uid, 股东号: $shareholderId)"
        USER1_UID=$uid
        USER1_SHAREHOLDER_ID=$shareholderId
    else
        print_failure "创建用户失败" "$response"
        return 1
    fi
    
    # 测试2: 查询所有用户
    print_test "1.2" "查询所有用户"
    response=$(curl -s ${ADMIN_URL}/api/users)
    if echo "$response" | grep -q "$USER1_SHAREHOLDER_ID"; then
        print_success "查询用户列表成功"
    else
        print_failure "查询用户列表失败" "$response"
    fi
    
    # 测试3: 按股东号查询
    print_test "1.3" "按股东号查询用户"
    response=$(curl -s ${ADMIN_URL}/api/users/shareholder/${USER1_SHAREHOLDER_ID})
    if echo "$response" | grep -q "$USER1_SHAREHOLDER_ID"; then
        print_success "按股东号查询成功"
    else
        print_failure "按股东号查询失败" "$response"
    fi
    
    # 测试4: 创建第二个用户（用于测试撮合）
    print_test "1.4" "创建第二个用户"
    response=$(curl -s -X POST ${ADMIN_URL}/api/users \
        -H "Content-Type: application/json" \
        -d '{"username":"测试用户2"}')
    
    if echo "$response" | grep -q "shareholderId"; then
        local uid=$(echo "$response" | grep -o '"uid":[0-9]*' | cut -d':' -f2)
        local shareholderId=$(echo "$response" | grep -o '"shareholderId":"[^"]*"' | cut -d'"' -f4)
        print_success "第二个用户创建成功 (UID: $uid, 股东号: $shareholderId)"
        USER2_UID=$uid
        USER2_SHAREHOLDER_ID=$shareholderId
    else
        print_failure "创建第二个用户失败" "$response"
    fi
}

# 测试下单功能
test_order_placement() {
    print_header "测试下单功能"
    
    # 测试5: 下买单
    print_test "2.1" "下买单"
    local response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"clOrderId\": \"BUY001\",
            \"market\": \"XSHG\",
            \"securityId\": \"600030\",
            \"side\": \"B\",
            \"qty\": 100,
            \"price\": 10.5,
            \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_ACK"; then
        print_success "买单下单成功"
        ORDER1_ID="BUY001"
    else
        print_failure "买单下单失败" "$response"
    fi
    
    # 测试6: 下卖单（不同用户，测试撮合）
    print_test "2.2" "下卖单（另一用户）"
    response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"clOrderId\": \"SELL001\",
            \"market\": \"XSHG\",
            \"securityId\": \"600030\",
            \"side\": \"S\",
            \"qty\": 50,
            \"price\": 10.5,
            \"shareholderId\": \"${USER2_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_ACK"; then
        print_success "卖单下单成功，应该会部分成交"
    else
        print_failure "卖单下单失败" "$response"
    fi
    
    # 测试7: 自动生成订单号
    print_test "2.3" "测试订单号自动生成"
    response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"market\": \"XSHG\",
            \"securityId\": \"600030\",
            \"side\": \"B\",
            \"qty\": 100,
            \"price\": 10.4,
            \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_ACK"; then
        local orderId=$(echo "$response" | grep -o '"clOrderId":"[^"]*"' | cut -d'"' -f4)
        print_success "订单号自动生成成功 (订单号: $orderId)"
        ORDER2_ID=$orderId
    else
        print_failure "订单号自动生成失败" "$response"
    fi
}

# 测试对敲风控
test_self_trade_prevention() {
    print_header "测试对敲风控功能"
    
    # 测试8: 同一股东号下买单后再下卖单（应被拒绝）
    print_test "3.1" "测试对敲风控（同股东同股票反向下单）"
    
    # 先下一个买单
    local response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"clOrderId\": \"SELFTRADE_BUY\",
            \"market\": \"XSHG\",
            \"securityId\": \"600000\",
            \"side\": \"B\",
            \"qty\": 100,
            \"price\": 15.0,
            \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_ACK"; then
        print_info "买单挂单成功"
        
        # 尝试同一股东号下卖单（应被拒绝）
        response=$(curl -s -X POST ${CORE_URL}/api/order \
            -H "Content-Type: application/json" \
            -d "{
                \"clOrderId\": \"SELFTRADE_SELL\",
                \"market\": \"XSHG\",
                \"securityId\": \"600000\",
                \"side\": \"S\",
                \"qty\": 100,
                \"price\": 15.1,
                \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
            }")
        
        if echo "$response" | grep -q "ORDER_REJECT" && echo "$response" | grep -q "1002"; then
            print_success "对敲风控成功拦截（rejectCode=1002）"
        else
            print_failure "对敲风控未生效" "$response"
        fi
    else
        print_failure "买单挂单失败，无法测试对敲" "$response"
    fi
}

# 测试撤单功能
test_order_cancellation() {
    print_header "测试撤单功能"
    
    # 测试9: 下单然后撤单
    print_test "4.1" "下单后撤单"
    
    # 先下单
    local response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"clOrderId\": \"CANCEL_TEST\",
            \"market\": \"XSHG\",
            \"securityId\": \"600050\",
            \"side\": \"B\",
            \"qty\": 100,
            \"price\": 20.0,
            \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_ACK"; then
        print_info "订单已挂单，准备撤单"
        
        # 撤单
        response=$(curl -s -X POST ${CORE_URL}/api/cancel \
            -H "Content-Type: application/json" \
            -d "{
                \"origClOrderId\": \"CANCEL_TEST\",
                \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
            }")
        
        if echo "$response" | grep -q "CANCEL_ACK"; then
            print_success "撤单成功"
        else
            print_failure "撤单失败" "$response"
        fi
    else
        print_failure "下单失败，无法测试撤单" "$response"
    fi
    
    # 测试10: 撤单号自动生成
    print_test "4.2" "撤单号自动生成"
    
    # 先下单
    response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"clOrderId\": \"CANCEL_TEST2\",
            \"market\": \"XSHG\",
            \"securityId\": \"600050\",
            \"side\": \"B\",
            \"qty\": 100,
            \"price\": 20.0,
            \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_ACK"; then
        # 撤单时不指定 clOrderId
        response=$(curl -s -X POST ${CORE_URL}/api/cancel \
            -H "Content-Type: application/json" \
            -d "{
                \"origClOrderId\": \"CANCEL_TEST2\",
                \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
            }")
        
        if echo "$response" | grep -q "CANCEL_ACK"; then
            local cancelId=$(echo "$response" | grep -o '"clOrderId":"[^"]*"' | cut -d'"' -f4)
            print_success "撤单号自动生成成功 (撤单号: $cancelId)"
        else
            print_failure "撤单失败" "$response"
        fi
    fi
}

# 测试查询功能
test_query_functions() {
    print_header "测试查询功能"
    
    # 测试11: 查询挂单
    print_test "5.1" "查询当前挂单"
    local response=$(curl -s "${CORE_URL}/api/orders?shareholderId=${USER1_SHAREHOLDER_ID}")
    
    if [ -n "$response" ]; then
        print_success "挂单查询成功"
        print_info "当前挂单: $response"
    else
        print_failure "挂单查询失败" "返回空结果"
    fi
    
    # 测试12: 查询历史订单
    print_test "5.2" "查询历史订单"
    response=$(curl -s "${ADMIN_URL}/api/orders/history?shareholderId=${USER1_SHAREHOLDER_ID}")
    
    if [ -n "$response" ]; then
        print_success "历史订单查询成功"
    else
        print_failure "历史订单查询失败" "返回空结果"
    fi
}

# 测试数据校验
test_validation() {
    print_header "测试数据校验功能"
    
    # 测试13: 非法市场
    print_test "6.1" "测试非法市场代码"
    local response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"market\": \"INVALID\",
            \"securityId\": \"600030\",
            \"side\": \"B\",
            \"qty\": 100,
            \"price\": 10.5,
            \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_REJECT" && echo "$response" | grep -q "1001"; then
        print_success "非法市场代码被正确拒绝（rejectCode=1001）"
    else
        print_failure "非法市场代码未被拒绝" "$response"
    fi
    
    # 测试14: 价格为0
    print_test "6.2" "测试价格为0"
    response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"market\": \"XSHG\",
            \"securityId\": \"600030\",
            \"side\": \"B\",
            \"qty\": 100,
            \"price\": 0,
            \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_REJECT"; then
        print_success "价格为0被正确拒绝"
    else
        print_failure "价格为0未被拒绝" "$response"
    fi
    
    # 测试15: 数量为0
    print_test "6.3" "测试数量为0"
    response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"market\": \"XSHG\",
            \"securityId\": \"600030\",
            \"side\": \"B\",
            \"qty\": 0,
            \"price\": 10.5,
            \"shareholderId\": \"${USER1_SHAREHOLDER_ID}\"
        }")
    
    if echo "$response" | grep -q "ORDER_REJECT"; then
        print_success "数量为0被正确拒绝"
    else
        print_failure "数量为0未被拒绝" "$response"
    fi
    
    # 测试16: 非法股东号
    print_test "6.4" "测试非法股东号"
    response=$(curl -s -X POST ${CORE_URL}/api/order \
        -H "Content-Type: application/json" \
        -d "{
            \"market\": \"XSHG\",
            \"securityId\": \"600030\",
            \"side\": \"B\",
            \"qty\": 100,
            \"price\": 10.5,
            \"shareholderId\": \"INVALID123\"
        }")
    
    if echo "$response" | grep -q "ORDER_REJECT" && echo "$response" | grep -q "1009"; then
        print_success "非法股东号被正确拒绝（rejectCode=1009）"
    else
        print_failure "非法股东号未被拒绝" "$response"
    fi
}

# 打印测试总结
print_summary() {
    print_header "测试总结"
    
    echo -e "总测试数: ${BLUE}${TOTAL_TESTS}${NC}"
    echo -e "通过测试: ${GREEN}${PASSED_TESTS}${NC}"
    echo -e "失败测试: ${RED}${FAILED_TESTS}${NC}"
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "\n${GREEN}========================================${NC}"
        echo -e "${GREEN}    所有测试通过！系统运行正常！    ${NC}"
        echo -e "${GREEN}========================================${NC}\n"
        return 0
    else
        echo -e "\n${RED}========================================${NC}"
        echo -e "${RED}   有 ${FAILED_TESTS} 个测试失败，请检查！   ${NC}"
        echo -e "${RED}========================================${NC}\n"
        return 1
    fi
}

# 主函数
main() {
    print_header "Trading System 功能测试"
    
    echo "开始测试前，请确保："
    echo "  1. 已启动 MySQL 数据库"
    echo "  2. 已启动所有服务（./start-all.sh）"
    echo "  3. 已安装 curl 和 grep 工具"
    echo ""
    
    read -p "是否继续测试？(y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "测试已取消"
        exit 0
    fi
    
    # 检查服务健康状态
    print_header "检查服务状态"
    check_service_health "trading-admin" "$ADMIN_URL" || exit 1
    check_service_health "trading-core" "$CORE_URL" || exit 1
    check_service_health "trading-gateway" "$GATEWAY_URL" || exit 1
    
    # 运行测试
    test_user_management
    test_order_placement
    test_self_trade_prevention
    test_order_cancellation
    test_query_functions
    test_validation
    
    # 打印总结
    print_summary
}

# 执行主函数
main
