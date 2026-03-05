# trading-core 模块功能与接口说明

本文档面向新手，详细说明 **trading-core**（交易核心）模块的功能、实现方案、主要类与接口、以及函数调用过程。Core 负责接收下单/撤单请求、做基本校验与对敲风控、调用 exchange-core 撮合引擎、并将引擎回报转换为需求文档规定的数据结构。

---

## 一、模块定位与职责

**trading-core** 是系统的**交易核心**，主要职责：

1. **交易转发**：接受下单（OrderRequest）与撤单（CancelRequest），转发给 exchange-core 引擎，并将引擎返回结果转换为订单确认/非法/撤单确认/撤单非法等回报。
2. **基本交易校验**：对订单做格式与规则校验（市场、证券、买卖方向、数量、价格、shareholderId 等）；clOrderId 可选，未传时系统自动生成（格式 ORD+时间戳+序号）。
3. **股东号校验**：通过 UserValidator 调用 Admin 接口校验股东号存在且启用，不存在或禁用则返回交易非法（如 1009），不进入引擎。
4. **对敲风控**：同一股东号下同市场同证券不能同时存在未成交的买与卖；会触发对敲的订单不进入撮合，返回 rejectCode=1002。
5. **撤单校验**：撤单仅需提供原订单编号（origClOrderId）；clOrderId（撤单请求编号）可选，未传时系统自动生成（格式 CAN+时间戳+序号）；其余字段可从 OpenOrderStore 挂单记录自动带出。
6. **挂单存储与查询**：维护当前未撤单的订单列表（OpenOrderStore），支持按股东号查询挂单；**成交后自动从挂单移除**（由 TradeEventProcessor 消费引擎 resultQueue 处理成交事件）。
7. **订单事件推送**：下单/撤单/成交后向 trading-admin 推送订单事件，供 Admin 落库维护订单历史。

**技术栈**：Spring Boot、exchange-core（外部引擎）。默认端口 **8081**。

---

## 二、功能列表与接口一览

| 功能 | HTTP 方法 | 路径 | 说明 |
|------|-----------|------|------|
| 下单 | POST | `/api/order` | Body: OrderRequest，返回 OrderAckResponse 或 OrderRejectResponse |
| 撤单 | POST | `/api/cancel` | Body: CancelRequest，返回 CancelAckResponse 或 CancelRejectResponse |
| 查询挂单 | GET | `/api/orders?shareholderId=xxx` | 某股东号下当前未撤单订单列表 |
| 健康检查 | GET | `/actuator/health` | Spring Actuator |

---

## 三、各功能实现方案与调用过程

### 3.1 下单（placeOrder）

#### 实现思路

1. **订单号**：若请求中 clOrderId 为空，则 `OrderService` 调用 `generateOrderId()` 生成（ORD+10 位秒级时间戳+4 位序号），并回填到请求中。
2. **基本校验**：`OrderValidation.validate(OrderRequest)` 校验非空、market、securityId、side、qty>0、price>0、shareholderId 非空且长度；clOrderId 若提供则校验长度，可不提供。
3. **股东号校验**：`UserValidator.validateShareholder(req)` 通过 HTTP 调用 Admin 的按股东号查询接口，若返回 404 或 4xx 则返回 OrderRejectResponse（如 1009），不进入引擎。
4. **对敲风控**：`SelfTradeChecker.checkAndRejectIfSelfTrade(req)` 检查同股东号+同市场+同证券下是否已有反向未成交挂单；若有则返回 rejectCode=1002，不进入引擎。
5. **引擎下单**：生成引擎 orderId，将价格×10000 转为引擎精度，构造 `ApiPlaceOrder`（GTC、BID/ASK），调用 `api.submitCommandAsyncFullResponse(cmd)` 同步等待结果。
6. **结果处理**：成功则维护 `clOrderIdToOrderId`、**向 TradeEventProcessor 注册 orderId→clOrderId 映射**、更新 SelfTradeChecker、写入 OpenOrderStore（若立即完全成交则移除挂单并发送 FILLED）、向 Admin 发送 PLACED/FILLED 事件、返回 `OrderAckResponse`（含 clOrderId）；失败则返回 `OrderRejectResponse`。

#### 函数调用过程

```
1. 客户端/Gateway POST /api/order，Body: OrderRequest
2. OrderController.placeOrder(request) → orderService.placeOrder(request)
3. OrderService.placeOrder(req)
   ├─ OrderValidation.validate(req) → 若非 null，return 该 OrderRejectResponse
   ├─ SelfTradeChecker.checkAndRejectIfSelfTrade(req) → 若非 null，return 该 OrderRejectResponse（对敲）
   ├─ engineHolder.getApi(); engineHolder.nextOrderId()
   ├─ 价格/数量/方向换算 → ApiPlaceOrder.builder()...build()
   ├─ api.submitCommandAsyncFullResponse(cmd).get(TIMEOUT_MS)
   ├─ 若 isSuccess(result.resultCode)：
   │  ├─ clOrderIdToOrderId.put(clOrderId, orderId)
   │  ├─ selfTradeChecker.onOrderAccepted(req, clOrderId, orderQty, filled)
   │  ├─ openOrderStore.add(OpenOrderRecord)
   │  ├─ sendOrderEvent("PLACED" 或 "FILLED", ...)  → OrderEventSender.sendEvent → Admin POST /internal/order-events
   │  └─ return toOrderAck(req, orderId, result)
   └─ 否则 return toOrderReject(req, toRejectCode(result.resultCode), ...)
4. Controller 将返回值 ResponseEntity.ok(result) 返回给调用方
```

---

### 3.2 撤单（cancelOrder）

#### 实现思路

1. **撤单号**：若请求中 clOrderId（本笔撤单的编号）为空，则系统自动生成（CAN+时间戳+序号）。
2. **挂单查找**：根据 `origClOrderId` 从 `OpenOrderStore` 取挂单记录；不存在则返回“订单不存在或已撤单”。若请求未带 shareholderId、market、securityId、side，则从挂单记录自动填充。
3. **撤单校验**：`CancelValidation.validate(req, openOrderStore)` 校验 origClOrderId 非空且长度、若挂单存在则股东号与挂单一致等。不通过则返回 `CancelRejectResponse`。
4. **查找引擎订单 ID**：从 `clOrderIdToOrderId` 或挂单记录的 engineOrderId 取 engineOrderId；不存在则返回“订单不存在”。
5. **引擎撤单**：构造 `ApiCancelOrder`，调用引擎异步接口并同步等待。
6. **结果处理**：成功则更新 SelfTradeChecker、从 OpenOrderStore 移除、删除 clOrderId 映射并**在 TradeEventProcessor 中取消注册**、向 Admin 发送 CANCELLED 事件、返回 `CancelAckResponse`；失败则返回 `CancelRejectResponse`。

#### 函数调用过程

```
1. 客户端/Gateway POST /api/cancel，Body: CancelRequest
2. OrderController.cancelOrder(request) → orderService.cancelOrder(request)
3. OrderService.cancelOrder(req)
   ├─ CancelValidation.validate(req, openOrderStore) → 若非 null，return 该 CancelRejectResponse
   ├─ engineOrderId = clOrderIdToOrderId.get(origClOrderId)；若 null → return toCancelReject(1004, "Order not found")
   ├─ ApiCancelOrder.builder().orderId(engineOrderId)...build()
   ├─ api.submitCommandAsyncFullResponse(cmd).get(TIMEOUT_MS)
   ├─ 若成功：
   │  ├─ selfTradeChecker.onCancelAccepted(origClOrderId, canceledQty)
   │  ├─ openOrderStore.remove(origClOrderId); clOrderIdToOrderId.remove(origClOrderId)
   │  ├─ sendOrderEvent("CANCELLED", ...)
   │  └─ return toCancelAck(req, result)
   └─ 否则 return toCancelReject(...)
4. Controller 返回 ResponseEntity.ok(result)
```

---

### 3.3 查询挂单（getOpenOrders）

- **实现思路**：`OrderController.getOpenOrders(shareholderId)` 调用 `OrderService.getOpenOrders(shareholderId)`，内部使用 `OpenOrderStore.listByShareholder(shareholderId)` 从内存映射中筛选该股东号的未撤单订单并返回。成交或撤单后的订单会从 OpenOrderStore 移除，故只显示当前未完全成交、未撤单的订单。
- **调用链**：`OrderController` → `OrderService.getOpenOrders` → `OpenOrderStore.listByShareholder` → `List<OpenOrderRecord>`。

---

### 3.4 成交与挂单状态同步（TradeEventProcessor）

- **作用**：引擎每笔命令的回报会通过 `ExchangeEngineHolder.resultsConsumer` 放入 `resultQueue`。**TradeEventProcessor** 在后台线程中消费该队列，解析 `OrderCommand.matcherEvent` 中的成交事件（TRADE），更新挂单状态并在完全成交时从挂单列表移除，同时向 Admin 发送 FILLED/PARTIALLY_FILLED 事件。
- **流程概要**：
  1. 启动时 `@PostConstruct` 启动消费线程，阻塞从 `resultQueue.take()`。
  2. 对每条 `OrderCommand`：若 `matcherEvent` 为空则忽略；否则遍历 TRADE 事件，累加 taker 订单成交数量，并处理**对手单（maker）**：根据 `matchedOrderId` 通过 `orderIdToClOrderId` 或 `OpenOrderStore.findClOrderIdByEngineOrderId` 找到 maker 的 clOrderId，调用 `updateOrderFillStatus` 更新其 filledQty，若完全成交则从 OpenOrderStore 移除并发送 FILLED 事件。
  3. 对当前订单（taker）：若在映射中则同样更新挂单、完全成交则移除并发送事件；若已在下单时立即完全成交（OrderService 已移除挂单），则仅取消注册映射。
- **关键点**：保证买卖双方在撮合成交后，挂单列表都会正确移除对应订单，历史订单状态由 Admin 根据事件更新。

---

### 3.5 校验与风控组件说明

#### OrderValidation（下单基本校验）

- **位置**：`com.trading.core.risk.OrderValidation`
- **作用**：需求文档 2.1.1「基本交易校验，无效订单输出交易非法回报」。
- **规则**：订单非空；clOrderId 若提供则≤16 字符（可不提供，由系统生成）；shareholderId 非空且≤10 字符；market 为 XSHG/XSHE/BJSE；securityId 非空且≤6 字符；side 为 B 或 S；qty>0；price>0。任一不满足即返回 `OrderRejectResponse`。

#### UserValidator（股东号校验）

- **位置**：`com.trading.core.risk.UserValidator`
- **作用**：调用 Admin 的按股东号查询接口，校验股东号存在且用户为启用状态；404 或 4xx 视为无效股东号，返回 OrderRejectResponse（如 1009），不进入引擎。

#### SelfTradeChecker（对敲风控）

- **位置**：`com.trading.core.risk.SelfTradeChecker`
- **作用**：需求文档 2.1.2「对敲风控：会触发对敲的订单不输出、并输出交易非法回报」。
- **逻辑**：按「股东号|市场|证券」维护当前未成交量（买/卖分开）。下单前若同 key 下已有反向未成交量>0，则拒绝并返回 rejectCode=1002。下单成功时 `onOrderAccepted` 增加该方向未成交量；撤单成功时 `onCancelAccepted` 扣减对应未成交量。

#### CancelValidation（撤单校验）

- **位置**：`com.trading.core.risk.CancelValidation`
- **作用**：撤单请求合法性（origClOrderId 非空与长度；可选：股东号与挂单一致）。
- **逻辑**：若 OpenOrderStore 中存在该 origClOrderId 的挂单，且请求带了 shareholderId，则必须与挂单的 shareholderId 一致，否则返回撤单非法。

---

### 3.6 引擎与存储

#### ExchangeEngineHolder

- **作用**：持有 exchange-core 实例，启动时创建引擎、注册默认 symbolId=1、uid=1、初始化余额；提供 `getApi()`、`nextOrderId()`；价格精度 10000（业务价格×10000 为引擎 long）。
- **与下单/撤单关系**：OrderService 通过 engineHolder 获取 API 和 orderId，构造 ApiPlaceOrder/ApiCancelOrder 提交给引擎。

#### OpenOrderStore

- **作用**：内存存储当前挂单（clOrderId → OpenOrderRecord），供撤单时校验、撤单/成交成功后移除、以及 GET /api/orders 按股东号列表查询。提供 `findClOrderIdByEngineOrderId(engineOrderId)` 供 TradeEventProcessor 根据引擎订单 ID 查找业务 clOrderId（用于处理 maker 订单成交）。

#### OrderEventSender

- **作用**：下单/撤单/成交后构造事件 Map（eventType、clOrderId、engineOrderId、shareholderId、market、securityId、side、price、orderQty、filledQty、canceledQty），POST 到 Admin 的 `/internal/order-events`。若配置了 `trading.admin.internal-token` 则带请求头。推送失败仅打日志，不影响下单/撤单结果。

---

## 四、回报与数据结构（与需求文档对应）

| 需求文档 | Core 实现 |
|----------|-----------|
| 订单确认回报 3.4 | OrderAckResponse |
| 订单非法回报 3.5 | OrderRejectResponse（rejectCode、rejectText） |
| 撤单确认回报 3.7 | CancelAckResponse（cumQty、canceledQty 等） |
| 撤单非法回报 3.8 | CancelRejectResponse |
| 成交回报 3.6 | 由引擎回报在 Core 侧可扩展；当前 PLACED/FILLED 事件中含成交信息，Admin 落库 |

market 使用 XSHG/XSHE/BJSE，side 使用 B/S，与需求文档一致。

---

## 五、配置要点

- **引擎**：exchange-core 需已安装到本地 Maven（exchange.core2:exchange-core）。
- **Admin 联动**：`trading.admin.base-url` 为 Admin 地址；`trading.admin.internal-token` 与 Admin 配置一致时，推送事件带 X-Internal-Token。

---

## 六、小结

| 功能 | 入口 | 核心逻辑与依赖 |
|------|------|----------------|
| 下单 | OrderController.placeOrder | 可选 clOrderId 生成 → OrderValidation → UserValidator → SelfTradeChecker → 引擎 → OpenOrderStore、TradeEventProcessor 注册、OrderEventSender |
| 撤单 | OrderController.cancelOrder | 可选撤单号生成、从 OpenOrderStore 补全请求 → CancelValidation → 引擎 → OpenOrderStore、TradeEventProcessor 取消注册、OrderEventSender |
| 成交与挂单同步 | TradeEventProcessor 后台线程 | 消费 resultQueue → 解析 matcherEvent → 更新/移除 OpenOrderStore、发送 FILLED/PARTIALLY_FILLED |
| 查挂单 | OrderController.getOpenOrders | OpenOrderStore.listByShareholder |

Core 不直接面向最终用户，通常由 Gateway 或测试脚本调用其 REST 接口；所有交易校验与对敲风控均在本模块完成，再与 exchange-core 交互完成撮合与回报。
