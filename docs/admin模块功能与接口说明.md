# trading-admin 模块功能与接口说明

本文档面向新手，详细说明 **trading-admin**（管理后台）模块的功能、实现方案、主要类与接口、以及函数调用过程。阅读后可以理解 Admin 在整体系统中的角色，并能按接口进行开发或联调。

---

## 一、模块定位与职责

**trading-admin** 是系统的**管理后台**，主要职责：

1. **用户（股东）管理**：创建、查询、禁用/启用用户；用户与股东号绑定，供交易核心校验。
2. **订单历史存储与查询**：接收 trading-core 推送的订单事件，落库维护历史订单状态，支持按股东号、状态分页查询。
3. **内部接口**：提供 `POST /internal/order-events` 供 Core 推送订单事件；可配置内部 Token 鉴权。

**技术栈**：Spring Boot、Spring Data JPA、MySQL。默认端口 **8080**。

---

## 二、功能列表与接口一览

| 功能 | HTTP 方法 | 路径 | 说明 |
|------|-----------|------|------|
| 创建用户 | POST | `/api/users` | 仅传 username，系统生成 uid、shareholderId；用户名重复返回 409 |
| 修改用户名 | PUT | `/api/users/{uid}/username` | 仅可修改 username，不可重复 |
| 查询用户余额 | GET | `/api/users/{uid}/balance` | 返回资金与持仓余额（简化实现） |
| 按 uid 查询用户 | GET | `/api/users/{uid}` | 单个用户 |
| 按股东号查询用户 | GET | `/api/users/shareholder/{shareholderId}` | 按股东号查 |
| 查询所有用户 | GET | `/api/users` | 列表 |
| 禁用用户 | POST | `/api/users/{uid}/suspend` | 将 status 置为 0 |
| 启用用户 | POST | `/api/users/{uid}/resume` | 将 status 置为 1 |
| 订单历史查询（分页） | GET | `/api/orders/history` | 可选 shareholderId、status、page、size |
| 删除单条历史订单 | DELETE | `/api/orders/history/{id}` | 按主键 id 删除，成功 204 |
| 删除全部历史订单 | DELETE | `/api/orders/history` | 清空历史表，返回 { deletedCount } |
| 接收订单事件（内部） | POST | `/internal/order-events` | Core 调用，可选 X-Internal-Token |
| 健康检查 | GET | `/actuator/health` | Spring Actuator |

---

## 三、各功能实现方案与调用过程

### 3.1 用户管理

#### 3.1.1 创建用户

- **实现思路**：Controller 接收 `CreateUserRequest`（仅含 **username**），调用 `UserService.createUser(username)`。Service 内：查最大 uid、+1 生成新 uid（起始 10000）；股东号格式为 `SH` + 8 位数字（与 uid 对应）；校验用户名不重复；构造 User（status=1）并保存。若用户名已存在则返回 **409 Conflict**。
- **主要类**：`UserController`、`UserService`、`UserRepository`、`User`（实体）。

**函数调用过程：**

```
1. 客户端 POST /api/users，Body: { "username": "测试用户" }
2. UserController.createUser(CreateUserRequest request)
   └─ userService.createUser(request.getUsername())
      ├─ userRepository.findMaxUid() → 新 uid = max + 1（无记录时从 10000 起）
      ├─ shareholderId = "SH" + String.format("%08d", uid % 100000000)
      ├─ userRepository 校验 username 是否已存在 → 存在则 throw
      ├─ 构造 User(uid, shareholderId, username, status=1)，setCreatedAt/setUpdatedAt
      └─ userRepository.save(user)
3. 返回 200 + 创建的用户（含系统生成的 uid、shareholderId）；用户名重复返回 409
```

#### 3.1.2 查询用户（按 uid / 按股东号 / 全部）

- **实现思路**：GET 请求到对应路径，Controller 调用 `UserService` 的 `getUserByUid`、`getUserByShareholderId` 或 `getAllUsers`，底层均为 JPA Repository 查询。不存在时返回 **404**。
- **调用链**（以按 uid 为例）：
  - `UserController.getUser(uid)` → `UserService.getUserByUid(uid)` → `UserRepository.findByUid(uid)` → `Optional<User>`，有则 200+data，无则 404。

#### 3.1.3 修改用户名

- **接口**：`PUT /api/users/{uid}/username`，Body: `{ "username": "新用户名" }`。
- **实现思路**：`UserService.updateUsername(uid, newUsername)` 校验用户名不与其它用户重复后更新并保存；不存在返回 404。

#### 3.1.4 查询用户余额

- **接口**：`GET /api/users/{uid}/balance`。
- **实现思路**：当前为简化实现，返回固定或共享的 quoteBalance、baseBalance（实际生产需按股东号映射引擎 uid 查询）。

#### 3.1.5 禁用 / 启用用户

- **实现思路**：POST `/api/users/{uid}/suspend` 或 `/resume`，Controller 调用 `UserService.suspendUser(uid)` 或 `resumeUser(uid)`，在 Service 内查用户、改 `status`（0=禁用，1=启用）、写回数据库。
- **调用链**：`UserController.suspendUser(uid)` → `UserService.suspendUser(uid)` → `findByUid` → `user.setStatus(0)` → `userRepository.save(user)`。

---

### 3.2 订单历史

#### 3.2.1 历史订单查询（分页）

- **实现思路**：`OrderHistoryController.history(shareholderId, status, page, size)` 调用 `OrderHistoryService.findHistory`。参数：`shareholderId`、`status`（LIVE/PARTIALLY_FILLED/FILLED/CANCELLED）可选；`page` 默认 0，`size` 默认 50、最大 500。按 `createdAt` 倒序，使用 JPA `PageRequest` + `Sort`。
- **主要类**：`OrderHistoryController`、`OrderHistoryService`、`OrderHistoryRepository`、`OrderHistory`（实体）。

**函数调用过程：**

```
1. GET /api/orders/history?shareholderId=A123&status=FILLED&page=0&size=20
2. OrderHistoryController.history(shareholderId, status, page, size)
   └─ orderHistoryService.findHistory(shareholderId, status, page, size)
      ├─ 校正 page/size（page>=0, size 1..500）
      ├─ Sort.by(DESC, "createdAt"), PageRequest.of(page, size, sort)
      ├─ 分支：
      │  · shareholderId 为空 → 按 status 或全部：findAllByOrderByCreatedAtDesc / findByStatusOrderByCreatedAtDesc
      │  · shareholderId 非空 → 按股东号+可选 status：findByShareholderIdOrderByCreatedAtDesc / findByShareholderIdAndStatusOrderByCreatedAtDesc
      └─ return List<OrderHistory>
3. Controller 返回 ResponseEntity.ok(list)
```

#### 3.2.2 接收订单事件（内部接口）

- **实现思路**：trading-core 在下单、撤单、成交时会向 Admin 的 `POST /internal/order-events` 推送事件。Admin 若配置了 `trading.admin.internal-token`，则校验请求头 `X-Internal-Token`，不一致则 **401**。通过后调用 `OrderHistoryService.processEvent(OrderEventDto)` 落库或更新状态。
- **事件类型**：PLACED、PARTIALLY_FILLED、FILLED、CANCELLED。DTO 含 clOrderId、engineOrderId、shareholderId、market、securityId、side、price、orderQty、filledQty、canceledQty 等。

**函数调用过程：**

```
1. Core 发起 POST /internal/order-events，Header: X-Internal-Token: xxx（若配置），Body: OrderEventDto
2. InternalOrderEventController.onOrderEvent(token, event)
   ├─ 若 internalToken 已配置：token 为空或不等于 internalToken → return 401
   ├─ event == null → return 400
   ├─ orderHistoryService.processEvent(event)
   │  ├─ eventType 为 PLACED / PARTIALLY_FILLED / FILLED：
   │  │  ├─ repository.findByClOrderId(clOrderId)
   │  │  ├─ 计算 status：filled>=orderQty ? FILLED : (filled>0 ? PARTIALLY_FILLED : LIVE)
   │  │  ├─ 若存在则 update filledQty、status、updatedAt；否则 insert 新 OrderHistory
   │  │  └─ repository.save(o)
   │  └─ eventType 为 CANCELLED：
   │     ├─ findByClOrderId → 若存在则 setStatus(CANCELLED)、setCanceledQty、save
   │     └─ 若不存在仅打日志
   └─ return 200
3. 异常时 return 500
```

#### 3.2.3 删除单条历史订单

- **接口**：`DELETE /api/orders/history/{id}`，`id` 为订单主键。
- **实现思路**：`OrderHistoryService.deleteById(id)` 若记录存在则 `repository.deleteById(id)` 并返回 **204 No Content**；不存在返回 **404**。

#### 3.2.4 删除全部历史订单

- **接口**：`DELETE /api/orders/history`（无路径参数）。
- **实现思路**：`OrderHistoryService.deleteAll()` 调用 `repository.deleteAll()`，返回 **200** 及 JSON `{ "deletedCount": number }`。

---

## 四、数据模型简述

- **User**：uid（主键）、shareholderId、username、status（0 禁用 1 启用）、createdAt、updatedAt。
- **OrderHistory**：id、clOrderId、engineOrderId、shareholderId、market、securityId、side、price、orderQty、filledQty、canceledQty、status、createdAt、updatedAt。

表结构由 JPA 自动建表或 DDL 管理，见 `application.yaml` 中 `spring.jpa.hibernate.ddl-auto`。

---

## 五、配置要点

- **数据库**：`spring.datasource.url`、`username`、`password`（可用环境变量 `DB_PASSWORD`）。
- **内部鉴权**：`trading.admin.internal-token`，配置后 Core 推送事件须带相同 `X-Internal-Token`。

---

## 六、小结

| 功能块 | 入口 | 核心逻辑位置 |
|--------|------|--------------|
| 用户 CRUD、修改用户名、查询余额 | UserController | UserService + UserRepository |
| 订单历史查询、删除单条/删除全部 | OrderHistoryController | OrderHistoryService + OrderHistoryRepository |
| 订单事件落库 | InternalOrderEventController | OrderHistoryService.processEvent |

Admin 不参与交易撮合，只负责“用户与订单历史”的存储与查询，以及接收 Core 的订单事件以保持历史表与交易状态一致。
