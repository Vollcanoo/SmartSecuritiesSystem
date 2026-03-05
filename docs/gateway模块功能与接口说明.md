# trading-gateway 模块功能与接口说明

本文档面向新手，详细说明 **trading-gateway**（交易网关）模块的功能、实现方案、主要类与接口、以及函数调用过程。Gateway 负责接收客户端通过 TCP 发送的一行 JSON，区分下单与撤单并转发到 trading-core，再将 Core 的回报原样写回客户端。

---

## 一、模块定位与职责

**trading-gateway** 是系统的**交易网关**，主要职责：

1. **协议接入**：在 TCP 端口（默认 **9000**）按行接收客户端消息，每行一条 JSON。
2. **请求识别**：若 JSON 中包含 `origClOrderId` 则视为**撤单**，否则视为**下单**。
3. **转发到 Core**：通过 HTTP 调用 trading-core 的 `POST /api/order` 或 `POST /api/cancel`，将解析后的 OrderRequest 或 CancelRequest 传给 Core。
4. **回报回写**：将 Core 返回的 JSON 原样写回客户端（一行）；若发生异常则返回统一错误格式 `{"error":"CODE"}`，不向客户端暴露内部细节。

**技术栈**：Spring Boot、Netty（TCP 服务端）。另提供 HTTP 端口 **8082**（如 Actuator 健康检查）。

---

## 二、功能列表与接口一览

| 功能 | 协议/端口 | 说明 |
|------|------------|------|
| 接收下单/撤单（一行 JSON） | TCP 9000 | 按行读 JSON，有 origClOrderId 则撤单，否则下单；回写一行 JSON |
| 健康检查 | HTTP 8082 | GET /actuator/health |

**说明**：业务逻辑只有“按行收 JSON → 转 Core → 回写结果”，无独立 REST 路径；客户端通过 TCP 连接与 Gateway 交互。

---

## 三、实现方案与调用过程

### 3.1 整体数据流

```
客户端 --[TCP 9000, 发送一行 JSON]--> Gateway
         Gateway 解析 JSON
           ├─ 含 origClOrderId → 撤单 → Core POST /api/cancel
           └─ 不含 origClOrderId → 下单 → Core POST /api/order
         Gateway 将 Core 返回的 JSON 写回客户端（一行）
```

### 3.2 启动与 Netty 管道

- **GatewayServer**（`CommandLineRunner`）：Spring 启动后执行 `run`，创建 Netty `ServerBootstrap`，监听 `config.getPort()`（默认 9000）。Pipeline：`LineBasedFrameDecoder(65536)` → `StringDecoder(UTF_8)` → `StringEncoder(UTF_8)` → `GatewayHandler`。即按行解码为字符串，再交给业务 Handler 处理；回写时用 StringEncoder 按 UTF-8 写出。
- **函数调用过程（启动）**：`GatewayServer.run()` → `ServerBootstrap.group(boss, worker).childHandler(...)` → `bind(port).sync()`，日志输出 "Gateway listening on port 9000"。

### 3.3 收到一行消息后的处理（GatewayHandler）

#### 实现思路

1. **解析 JSON**：使用 Jackson `ObjectMapper.readTree(line)` 解析一行字符串；若解析失败则视为非法 JSON，返回 `{"error":"INVALID_JSON"}`。
2. **区分下单/撤单**：若 `root.has("origClOrderId")` 为 true，则 `treeToValue(root, CancelRequest.class)` 得到撤单请求，否则 `treeToValue(root, OrderRequest.class)` 得到下单请求。
3. **调用 Core**：撤单调用 `CoreClient.cancelOrder(req)`，下单调用 `CoreClient.placeOrder(req)`。CoreClient 内部用 RestTemplate 请求 Core 的 `/api/cancel` 或 `/api/order`，返回响应体字符串（或异常）。
4. **回写客户端**：将 Core 返回的字符串作为一行写回（`ctx.writeAndFlush(response + "\n")`）。若 Core 调用抛异常，则捕获后写回 `{"error":"BAD_REQUEST"}`；其他异常在 `exceptionCaught` 中写回 `{"error":"INTERNAL_ERROR"}`。

#### 函数调用过程

```
1. 客户端发送一行，例如：{"clOrderId":"o1","market":"XSHG","securityId":"600000","side":"B","qty":100,"price":10.5,"shareholderId":"A123"}
2. Netty Pipeline：LineBasedFrameDecoder 截取一行 → StringDecoder 得到 String → GatewayHandler.channelRead0(ctx, msg)
3. GatewayHandler.channelRead0(ctx, line)
   ├─ msg 为空或 trim 为空 → return（不响应）
   ├─ objectMapper.readTree(line) → 若 JsonProcessingException → writeError(ctx, "INVALID_JSON"); return
   ├─ if (root.has("origClOrderId"))：
   │  ├─ CancelRequest req = objectMapper.treeToValue(root, CancelRequest.class)
   │  ├─ coreClient.cancelOrder(req)  → RestTemplate POST config.getCoreBaseUrl()+"/api/cancel"
   │  └─ response = objectMapper.writeValueAsString(result) 或 result 若已是 String
   └─ else：
   │  ├─ OrderRequest req = objectMapper.treeToValue(root, OrderRequest.class)
   │  ├─ coreClient.placeOrder(req)   → RestTemplate POST config.getCoreBaseUrl()+"/api/order"
   │  └─ response = ...
   ├─ ctx.writeAndFlush(response + "\n")
   └─ 若 Core 调用抛异常 → writeError(ctx, "BAD_REQUEST")
4. 若 Handler 内未捕获异常 → exceptionCaught → writeError(ctx, "INTERNAL_ERROR")
5. writeError：ctx.writeAndFlush("{\"error\":\"CODE\"}\n")
```

### 3.4 CoreClient

- **作用**：封装对 trading-core 的 HTTP 调用，不修改 Core 与引擎代码。
- **placeOrder**：POST `{coreBaseUrl}/api/order`，Body 为 OrderRequest（JSON），返回响应体（String），异常时抛出 RuntimeException，由 GatewayHandler 捕获并返回 BAD_REQUEST。
- **cancelOrder**：POST `{coreBaseUrl}/api/cancel`，Body 为 CancelRequest（JSON），同上。

配置项 `trading.gateway.core-base-url` 默认为 `http://localhost:8081`，需与 Core 实际地址一致。

---

## 四、错误码（对客户端）

| 错误码 | 含义 |
|--------|------|
| INVALID_JSON | 请求行不是合法 JSON |
| BAD_REQUEST | 请求格式正确但 Core 调用失败（如网络、Core 返回非 2xx） |
| INTERNAL_ERROR | Handler 内部未预期异常 |

客户端可根据 `{"error":"xxx"}` 做重试或提示，不暴露服务端堆栈或内部 URL。

---

## 五、配置要点

- **trading.gateway.port**：TCP 监听端口，默认 9000。
- **trading.gateway.core-base-url**：trading-core 的 base URL，默认 http://localhost:8081。Gateway 启动前需确保 Core 已启动，否则下单/撤单会得到 BAD_REQUEST。

---

## 六、小结

| 环节 | 类/组件 | 说明 |
|------|---------|------|
| TCP 服务端 | GatewayServer | Netty 按行解码，将 String 交给 GatewayHandler |
| 业务处理 | GatewayHandler | 解析 JSON → 区分下单/撤单 → CoreClient 调 Core → 回写一行 |
| 调用 Core | CoreClient | RestTemplate 调用 /api/order、/api/cancel |

Gateway 本身不做业务校验与撮合，只做协议转换与转发；所有校验、风控、撮合均在 trading-core 完成。
