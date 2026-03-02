# 测试 API 示例（项目已启动时使用）

- **trading-admin**：http://localhost:8080  
- **trading-core**：http://localhost:8081  
- **trading-gateway**：TCP 9000（业务）、HTTP 8082（Actuator）

**说明**：当前接口无登录鉴权，仅适合内网/测试。若 Admin 配置了 `trading.admin.internal-token`，则 Core 推送订单事件时须带请求头 `X-Internal-Token`（与配置一致）；该内部接口不建议手工调用。

---

## 零、通俗理解：买单 / 卖单 & 各字段含义

### 买单和卖单是什么？

可以想成你在**挂单**，告诉交易所“我想按什么价格、买/卖多少”。

| 概念 | 通俗理解 | 你付出什么 | 你得到什么 |
|------|----------|------------|------------|
| **买单** | 你想**买**股票：出钱换股 | 钱（按价格×数量冻结） | 股票（成交后到账） |
| **卖单** | 你想**卖**股票：出股换钱 | 股票（按数量冻结） | 钱（成交后到账） |

- **买单**：你挂“我愿用 10.5 元/股，买 100 股” → 系统先扣/冻你 10.5×100 的钱，等有人按这个价卖给你就成交。  
- **卖单**：你挂“我愿用 10.6 元/股，卖 100 股” → 系统先扣/冻你 100 股，等有人按这个价买就成交。

**撮合**：有人买、有人卖，价格能对上就成交（买价 ≥ 卖价即可成交）。

---

### 下单接口里 JSON 各字段是什么意思？

**下单**（买或卖）都走同一个接口，用 `side` 区分买/卖；请求体里常见字段含义如下：

| 字段 | 含义 | 示例 | 说明 |
|------|------|------|------|
| **clOrderId** | 客户订单号 | `"ord001"` | 你自己起的、本笔委托的唯一编号，用来查单、撤单时指“撤哪一笔”（撤单时用 origClOrderId 填要撤的这笔的 clOrderId） |
| **market** | 市场 | `"XSHG"` | 哪个市场：沪市 XSHG、深市 XSHE、北交所 BJSE |
| **securityId** | 股票代码 | `"600030"` | 哪只股票，如 600030 |
| **side** | 买还是卖 | `"B"` 或 `"S"` | B = 买单，S = 卖单（也可写 "BUY" / "SELL"） |
| **qty** | 数量 | `100` | 买或卖多少股（整数） |
| **price** | 价格 | `10.5` | 你愿意成交的单价（元），如 10.5 表示 10.5 元/股 |
| **shareholderId** | 股东号 | `"A123456789"` | 用哪个股东账户下单，必须在 Admin 里先建好且未被禁用 |

**撤单**接口里多出来的字段：

| 字段 | 含义 | 示例 | 说明 |
|------|------|------|------|
| **origClOrderId** | 要撤掉的那笔单的客户订单号 | `"ord001"` | 填你当初下单时用的 clOrderId，表示“撤这一笔” |
| **clOrderId** | 本笔撤单的编号 | `"cancel001"` | 本笔撤单操作自己的唯一编号，和下单的 clOrderId 一样自己起名即可 |
| **side** | 原订单方向 | `"B"` 或 `"S"` | 要撤的那笔是买单就填 B，卖单就填 S，其他字段（market、securityId、shareholderId）要和原订单一致 |

---

## 一、管理后台 Admin（端口 8080）

### 1. 创建用户（需先有用户，Core 下单时会校验股东号）

仅需传 **username**，系统自动生成 uid、股东号（格式 SH+8 位数字）：

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"测试用户1"}'
```

### 2. 查询所有用户

```bash
curl -s http://localhost:8080/api/users
```

### 3. 按 uid 查询

```bash
curl -s http://localhost:8080/api/users/10001
```

### 4. 按股东号查询

```bash
curl -s http://localhost:8080/api/users/shareholder/A123456789
```

### 5. 禁用用户

```bash
curl -X POST http://localhost:8080/api/users/10001/suspend
```

### 6. 启用用户

```bash
curl -X POST http://localhost:8080/api/users/10001/resume
```

### 7. 查询订单历史（按股东号、状态，分页，数据库表维护）

```bash
# 某股东号历史订单，分页（page 从 0 开始，size 默认 50，最大 500）
curl -s "http://localhost:8080/api/orders/history?shareholderId=A123456789"
curl -s "http://localhost:8080/api/orders/history?shareholderId=A123456789&page=0&size=20"

# 按状态筛选：LIVE=挂单中, FILLED=全部成交, CANCELLED=已撤单, PARTIALLY_FILLED=部分成交
curl -s "http://localhost:8080/api/orders/history?shareholderId=A123456789&status=FILLED"
```

说明：订单由 trading-core 在下单/撤单/成交时推送到 admin 落库，状态由事件自动更新。

### 7.1 删除订单历史

```bash
# 删除单条（id 为历史表主键，成功返回 204 无内容）
curl -s -X DELETE "http://localhost:8080/api/orders/history/1"

# 删除全部历史订单（返回 JSON：{ "deletedCount": n }）
curl -s -X DELETE "http://localhost:8080/api/orders/history"
```

### 8. 健康检查（Actuator）

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
```

### 9. 内部接口（仅 Core 调用，不建议手工调用）

- **POST /internal/order-events**：Core 推送订单事件。若 Admin 配置了 `trading.admin.internal-token`，请求头须带 `X-Internal-Token: <与配置一致>`，否则返回 401。

---

## 二、交易核心 Core（端口 8081）

**说明**：下单/撤单使用的 `shareholderId` 需在 Admin 中已创建且未被禁用；市场代码为 `XSHG`（沪）/ `XSHE`（深）/ `BJSE`（北）。

### 1. 下单（买单）

```bash
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "clOrderId": "ord001",
    "market": "XSHG",
    "securityId": "600030",
    "side": "B",
    "qty": 100,
    "price": 10.5,
    "shareholderId": "A123456789"
  }'
```

### 2. 下单（卖单）

```bash
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "clOrderId": "ord002",
    "market": "XSHG",
    "securityId": "600030",
    "side": "S",
    "qty": 100,
    "price": 10.6,
    "shareholderId": "A123456789"
  }'
```

### 3. 查询某股东号的当前挂单

```bash
curl -s "http://localhost:8081/api/orders?shareholderId=A123456789"
```

返回该股东号下所有**未撤单**的订单列表（含 clOrderId、market、securityId、side、price、orderQty、filledQty、engineOrderId 等）。

### 4. 撤单（origClOrderId 填要撤掉的那笔订单的 clOrderId）

```bash
curl -X POST http://localhost:8081/api/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "clOrderId": "cancel001",
    "origClOrderId": "ord001",
    "market": "XSHG",
    "securityId": "600030",
    "shareholderId": "A123456789",
    "side": "B"
  }'
```

---

## 三、推荐测试顺序

1. **Admin 创建用户**：执行上面「1. 创建用户」。
2. **Admin 查用户**：执行「2. 查询所有用户」或「3. 按 uid 查询」确认用户存在。
3. **Core 下单**：执行「二、1. 下单（买单）」和「二、2. 下单（卖单）」。
4. **Core 撤单**：执行「二、3. 撤单」（撤掉 `ord001`）。

若下单返回对敲/股东号等错误，请确认该 `shareholderId` 已在 Admin 中创建且状态为启用。

---

## 四、可正常卖单的完整流程（不修改代码）

同股东号下若已有未成交的**买单**，再下**卖单**会被对敲风控拒绝（1002）。要**正常成交卖单**，请按顺序执行：

### 步骤 1：先撤掉该股东号下的未成交买单

（将下面 `ord001` 换成你实际要撤的买单的 `clOrderId`。）

```bash
curl -X POST http://localhost:8081/api/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "clOrderId": "cancel001",
    "origClOrderId": "ord001",
    "market": "XSHG",
    "securityId": "600030",
    "shareholderId": "A123456789",
    "side": "B"
  }'
```

### 步骤 2：再下卖单

```bash
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "clOrderId": "ord002",
    "market": "XSHG",
    "securityId": "600030",
    "side": "S",
    "qty": 100,
    "price": 10.6,
    "shareholderId": "A123456789"
  }'
```

**说明**：引擎启动时已为默认用户注入资金（报价货币）与持仓（基础货币），因此按「先撤买单、再下卖单」顺序执行后，卖单应能正常通过。
