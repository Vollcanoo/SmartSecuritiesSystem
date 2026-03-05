# 需求功能与模块对照及全功能测试 API

本文档依据 **《模拟股票交易对敲撮合系统 Elvis Wang》** 中的基础目标与高级目标，逐条说明**各功能由哪个模块、哪些类/接口完成**，并为**所有已实现功能**提供可执行的测试 API 示例（curl 或 nc），便于新手验证与联调。

---

## 一、基础目标

### 2.1.1 交易转发

| 需求描述 | 实现模块 | 实现位置与说明 |
|----------|----------|----------------|
| 实现模拟交易转发逻辑，接受输入交易订单，输出交易订单 | **trading-gateway** + **trading-core** | **Gateway**：`GatewayHandler` 按行接收 JSON，无 `origClOrderId` 则解析为 `OrderRequest`，经 `CoreClient.placeOrder(req)` 转发到 Core。**Core**：`OrderController.placeOrder` → `OrderService.placeOrder` → 校验与风控通过后调用 exchange-core 引擎，将引擎回报转为 `OrderAckResponse` 或 `OrderRejectResponse` 返回。 |
| 实现模拟回报转发逻辑，接受输入回报，输出回报 | **trading-core** + **trading-gateway** | **Core**：引擎返回结果后，OrderService 构造订单确认/非法回报（OrderAckResponse/OrderRejectResponse），通过 Controller 返回。**Gateway**：将 Core 返回的 JSON 原样写回客户端（一行）。 |
| 实现基本的交易校验功能，对于简单的无效订单，输出一条交易非法的回报 | **trading-core** | **OrderValidation**（`com.trading.core.risk.OrderValidation`）：在下单前校验 market∈{XSHG,XSHE,BJSE}、securityId 非空且≤6 字符、side 为 B/S、qty>0、price>0、clOrderId/shareholderId 非空且长度符合要求；不通过则返回 `OrderRejectResponse`（对应 RejectCode），不进入引擎。 |

**测试 API（交易转发 + 基本校验）：**

```bash
# 1. 正常下单（转发成功，应返回订单确认回报）
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"t1","market":"XSHG","securityId":"600030","side":"B","qty":100,"price":10.5,"shareholderId":"A123"}'

# 2. 无效订单：错误市场（应返回订单非法回报，rejectCode 约 1006）
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"t2","market":"SH","securityId":"600030","side":"B","qty":100,"price":10.5,"shareholderId":"A123"}'

# 3. 无效订单：数量为 0（应返回订单非法回报，rejectCode 约 1008）
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"t3","market":"XSHG","securityId":"600030","side":"B","qty":0,"price":10.5,"shareholderId":"A123"}'

# 4. 经 Gateway 转发（TCP 一行 JSON，应收到一行回报）
echo '{"clOrderId":"g1","market":"XSHG","securityId":"600030","side":"B","qty":100,"price":10.5,"shareholderId":"A123"}' | nc localhost 9000
```

---

### 2.1.2 对敲风控

| 需求描述 | 实现模块 | 实现位置与说明 |
|----------|----------|----------------|
| 实现对于会触发对敲交易的交易订单的过滤，不输出该交易订单 | **trading-core** | **SelfTradeChecker**（`com.trading.core.risk.SelfTradeChecker`）：在 `OrderService.placeOrder` 中，在送引擎前调用 `checkAndRejectIfSelfTrade(req)`。同股东号+同市场+同证券下若已有反向未成交挂单，则拒绝该笔订单，不进入引擎。 |
| 实现对于会触发对敲交易的交易订单，输出一条交易非法的回报 | **trading-core** | 同上：拒绝时返回 `OrderRejectResponse`，rejectCode=**1002**，rejectText 含“对敲”相关说明。 |

**测试 API（对敲风控）：**

```bash
# 1. 先下一笔买单（成功）
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"self1","market":"XSHG","securityId":"600000","side":"B","qty":100,"price":10.5,"shareholderId":"A123"}'

# 2. 同一股东号、同市场同证券再下一笔卖单（应被对敲拒绝，rejectCode=1002）
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"self2","market":"XSHG","securityId":"600000","side":"S","qty":100,"price":10.6,"shareholderId":"A123"}'
# 预期：返回 JSON 中含 "rejectCode":1002

# 3. 撤掉第一笔买单后再下卖单（应成功）
curl -s -X POST http://localhost:8081/api/cancel \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"c1","origClOrderId":"self1","market":"XSHG","securityId":"600000","shareholderId":"A123","side":"B"}'
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"self2","market":"XSHG","securityId":"600000","side":"S","qty":100,"price":10.6,"shareholderId":"A123"}'
```

---

### 2.1.3 模拟撮合

| 需求描述 | 实现模块 | 实现位置与说明 |
|----------|----------|----------------|
| 实现对敲交易订单端自动撮合，返回成交回报 | **trading-core**（依赖 **exchange-core**） | **OrderService** 将合法订单通过 `ExchangeEngineHolder.getApi().submitCommandAsyncFullResponse(ApiPlaceOrder)` 提交给 exchange-core；引擎按价格优先、时间优先撮合，回报通过 OrderCommand 返回；Core 将结果转为 `OrderAckResponse`（含 orderId 等）。成交回报由引擎产生，Core 可通过事件推送将成交信息发给 Admin。 |
| 被匹配的对手方交易订单需要从交易所侧撤回 | **exchange-core** | 由引擎内部逻辑处理；Core 不修改引擎代码，仅调用其 API。 |
| 设计成交价生成算法 | **exchange-core** | 成交价由引擎内部规则决定；Core 层不实现单独算法。 |
| 处理零股成交 | **trading-core** + **exchange-core** | 当前数量为整数（qty），引擎按数量撮合；A 股最小单位 1 股，零股在业务层未单独扩展。 |

**测试 API（撮合与回报）：**

```bash
# 不同股东号：A 买、B 卖，可撮合（需先在 Admin 创建两个用户/股东号）
# 1. 用户 A 买单
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"m1","market":"XSHG","securityId":"600030","side":"B","qty":100,"price":10.5,"shareholderId":"A123"}'

# 2. 用户 B 卖单（价格<=买价可成交，由引擎决定）
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"m2","market":"XSHG","securityId":"600030","side":"S","qty":100,"price":10.5,"shareholderId":"B456"}'
# 预期：两笔均返回订单确认；若撮合成功，回报中会体现引擎结果
```

---

## 二、高级目标（可选）

### 2.2.1 行情接入

| 需求描述 | 实现模块 | 实现位置与说明 |
|----------|----------|----------------|
| 考虑行情信息：能读取解析输入的行情信息 | **未实现** | 当前无行情接入模块。 |
| 在撮合时考虑行情信息：撮合时需要保证买价、卖价和对手方价格保持一致 | **未实现** | 撮合完全由 exchange-core 完成，未接入行情。 |

**测试 API**：无（功能未实现）。

---

### 2.2.2 撤单支持

| 需求描述 | 实现模块 | 实现位置与说明 |
|----------|----------|----------------|
| 处理输入的撤单请求 | **trading-gateway** + **trading-core** | **Gateway**：JSON 含 `origClOrderId` 时解析为 `CancelRequest`，调用 `CoreClient.cancelOrder(req)` 转发。**Core**：`OrderController.cancelOrder` → `OrderService.cancelOrder` → `CancelValidation.validate` → 根据 `origClOrderId` 查 `clOrderIdToOrderId` 得引擎 orderId → 调用引擎 `ApiCancelOrder` → 返回 `CancelAckResponse` 或 `CancelRejectResponse`。 |

**测试 API（撤单）：**

```bash
# 1. 先下一笔单
curl -s -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"cancelOrd1","market":"XSHG","securityId":"600030","side":"B","qty":100,"price":10.5,"shareholderId":"A123"}'

# 2. 撤单（origClOrderId 为上面那笔的 clOrderId）
curl -s -X POST http://localhost:8081/api/cancel \
  -H "Content-Type: application/json" \
  -d '{"clOrderId":"cancelReq1","origClOrderId":"cancelOrd1","market":"XSHG","securityId":"600030","shareholderId":"A123","side":"B"}'
# 预期：返回撤单确认（含 cumQty、canceledQty 等）

# 3. 经 Gateway 撤单（TCP）
echo '{"clOrderId":"cancelReq2","origClOrderId":"cancelOrd1","market":"XSHG","securityId":"600030","shareholderId":"A123","side":"B"}' | nc localhost 9000
```

---

### 2.2.3 性能优化

| 需求描述 | 实现模块 | 实现位置与说明 |
|----------|----------|----------------|
| 分析性能瓶颈 | - | 可选后续工作。 |
| 面向吞吐量/低延时优化 | - | 可选后续工作。 |

**测试 API**：无（无单独性能测试接口，可按压测工具对 Core/Gateway 做压力测试）。

---

### 2.2.4 管理界面

| 需求描述 | 实现模块 | 实现位置与说明 |
|----------|----------|----------------|
| 实现可视化前端展示界面框架 | **未实现** | 当前无前端页面。 |
| 实现可视化展示当前订单状态 | **部分** | **trading-admin** 提供 **订单历史** 查询 API（`GET /api/orders/history`），**trading-core** 提供 **当前挂单** 查询 API（`GET /api/orders?shareholderId=`），可由前端调用展示。 |
| 实现手动输入交易订单入口 | **trading-gateway** + **trading-core** | 通过 TCP 9000 或直接调 Core 的 `POST /api/order` 即“手动输入”订单；无 Web 表单界面。 |

**测试 API（订单状态与历史）：**

```bash
# 当前挂单（Core）
curl -s "http://localhost:8081/api/orders?shareholderId=A123"

# 订单历史（Admin，分页、按状态筛选）
curl -s "http://localhost:8080/api/orders/history?shareholderId=A123&page=0&size=20"
curl -s "http://localhost:8080/api/orders/history?shareholderId=A123&status=FILLED"

# 删除单条历史订单（id 为历史记录主键，成功 204）
curl -s -X DELETE "http://localhost:8080/api/orders/history/1"
# 删除全部历史订单（返回 { "deletedCount": n }）
curl -s -X DELETE "http://localhost:8080/api/orders/history"
```

---

### 2.2.5 数据分析

| 需求描述 | 实现模块 | 实现位置与说明 |
|----------|----------|----------------|
| 实现交易历史记录存储功能，便利离线分析 | **trading-admin** | **InternalOrderEventController** 接收 Core 推送的 `POST /internal/order-events`；**OrderHistoryService.processEvent** 将事件落库到 `OrderHistory`（JPA），支持 PLACED/PARTIALLY_FILLED/FILLED/CANCELLED 等状态更新。 |
| 实现交易历史记录分析功能，输出分析结果 | **未实现** | 当前仅有历史查询与分页，无统计/分析接口。 |

**测试 API（历史存储与查询）：**

```bash
# 历史由 Core 推送事件自动落库，无需单独“写入”接口；查询如下：
curl -s "http://localhost:8080/api/orders/history?shareholderId=A123&page=0&size=50"
curl -s "http://localhost:8080/api/orders/history?status=CANCELLED&page=0&size=20"
```

---

## 三、管理后台与用户相关功能（补充）

以下功能对应 Admin 模块，与需求文档中的“用户/管理”相关，一并列出便于测试。

| 功能 | 实现模块 | 实现位置 |
|------|----------|----------|
| 用户创建 / 查询 / 禁用 / 启用 | **trading-admin** | UserController、UserService、UserRepository |

**测试 API：**

```bash
# 创建用户
curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"uid":10001,"shareholderId":"A123456789","username":"测试用户1"}'

# 查询所有用户
curl -s http://localhost:8080/api/users

# 按 uid 查询
curl -s http://localhost:8080/api/users/10001

# 按股东号查询
curl -s http://localhost:8080/api/users/shareholder/A123456789

# 禁用用户
curl -s -X POST http://localhost:8080/api/users/10001/suspend

# 启用用户
curl -s -X POST http://localhost:8080/api/users/10001/resume
```

---

## 四、健康检查与内部接口

| 功能 | 地址 | 说明 |
|------|------|------|
| Admin 健康检查 | GET http://localhost:8080/actuator/health | 用于运维探测 |
| Core 健康检查 | GET http://localhost:8081/actuator/health | 同上 |
| Gateway 健康检查 | GET http://localhost:8082/actuator/health | 同上 |
| 内部订单事件（Core→Admin） | POST http://localhost:8080/internal/order-events | 由 Core 调用；若配置 internal-token 须带 X-Internal-Token |

**测试 API：**

```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8082/actuator/health
```

---

## 五、文档索引

- **各模块详细功能与调用过程**：`admin模块功能与接口说明.md`、`core模块功能与接口说明.md`、`gateway模块功能与接口说明.md`
- **项目使用说明**：`项目使用说明.md`
- **项目概述**：`项目说明书.md`
- **需求原文**：`模拟股票交易对敲撮合系统 Elvis Wang.md`
- **更多 curl 示例与字段说明**：`测试API示例.md`
