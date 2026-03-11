# 模拟股票交易对敲撮合系统 - 前端使用说明

本文档说明如何部署和使用本系统的前端页面。

---

## 一、项目结构

```
trading-frontend/
├── index.html          # 系统概览/仪表板
├── users.html          # 用户管理页面
├── trading.html        # 交易下单页面
├── orders.html         # 订单历史查询页面（含删除单条/删除全部）
├── open-orders.html    # 当前挂单查询页面
├── market.html         # 行情查看与推送页面
├── start-server.sh     # 启动脚本（Python HTTP 服务器）
├── css/
│   └── style.css      # 样式文件
├── js/
│   ├── config.js      # API 配置
│   ├── api.js         # API 调用封装与工具函数
│   ├── dashboard.js   # 仪表板逻辑
│   ├── users.js       # 用户管理逻辑
│   ├── trading.js     # 交易下单逻辑
│   ├── orders.js      # 订单历史逻辑（含删除）
│   ├── open-orders.js # 挂单查询逻辑
│   └── market.js      # 行情查看与推送逻辑
├── README.md          # 本文档
└── TROUBLESHOOTING.md  # 常见问题排查
```

---

## 二、部署方式

### 方式一：使用启动脚本（推荐）

在 `trading-frontend` 目录下执行：

```bash
./start-server.sh
```

然后在浏览器访问：`http://localhost:8000`

### 方式二：Python HTTP 服务器

```bash
cd trading-frontend
python3 -m http.server 8000
```

然后访问：`http://localhost:8000`

### 方式三：Node.js http-server

```bash
npm install -g http-server
cd trading-frontend
http-server -p 8000
```

### 前置条件

- **trading-admin** 运行在 `http://localhost:8080`
- **trading-core** 运行在 `http://localhost:8081`

**注意**：直接双击打开 HTML 可能遇到 CORS 跨域问题，请使用上述 HTTP 服务方式访问。

---

## 三、配置说明

### API 地址（js/config.js）

- Admin API：`http://localhost:8080`
- Core API：`http://localhost:8081`

修改时编辑 `js/config.js` 中的 `CONFIG.ADMIN_BASE_URL` 和 `CONFIG.CORE_BASE_URL`。

---

## 四、功能说明

### 4.1 系统概览（index.html）

- **服务状态**：显示 Admin、Core 健康状态
- **用户总数**：当前系统用户数
- **今日订单**：今日订单数量（简化统计）
- **快速入口**：跳转用户管理、交易、订单历史、挂单

每 30 秒自动刷新一次统计。

### 4.2 用户管理（users.html）

#### 创建用户

- **用户名 (username)**：必填，系统内唯一，最多 50 字符
- **用户ID、股东号**：由系统自动生成，无需填写
  - 用户ID：从 10000 起递增
  - 股东号：格式 `SH` + 8 位数字（如 `SH00010001`）

创建成功后列表会显示新用户及其 uid、shareholderId。

#### 用户列表

- 展示：用户ID、股东号、用户名、状态、创建时间
- **搜索**：按股东号筛选
- **操作**：禁用/启用用户（影响该股东号能否下单）

#### 其他

- **修改用户名**：在用户列表中可修改用户名（不可与已有用户重复）
- **查询余额**：可查询用户资金与持仓余额（当前为简化实现，多用户共享引擎余额）

### 4.3 交易下单（trading.html）

#### 下单

- **订单编号 (clOrderId)**：选填；不填时由系统自动生成（格式 `ORD` + 时间戳 + 序号），成功后返回该编号供撤单使用
- **股东号 (shareholderId)**：必填，须为 Admin 中已创建且启用的用户
- **市场 (market)**：XSHG / XSHE / BJSE
- **股票代码 (securityId)**：如 600030
- **买卖方向 (side)**：B 买 / S 卖
- **数量 (qty)**、**价格 (price)**：必填，正数

成功后会显示订单编号（含系统生成的）；失败会显示错误码与原因（如对敲风控、股东号不存在等）。

#### 撤单

- **原订单编号 (origClOrderId)**：必填，即下单成功返回的 clOrderId
- **撤单编号 (clOrderId)**：选填，不填时由系统自动生成
- 市场、股票、股东号、方向可由系统从挂单记录自动带出，一般只需填原订单编号

### 4.4 订单历史（orders.html）

#### 查询

- **股东号**：可选
- **状态**：可选，LIVE / PARTIALLY_FILLED / FILLED / CANCELLED
- **每页条数**：20 / 50 / 100

点击「查询」后展示分页列表。

#### 列表与操作

- 列：订单编号、股东号、市场、股票代码、方向、价格、数量、已成交、已撤单、状态、创建时间、**操作**
- **删除**：每行右侧「删除」按钮，确认后删除该条历史记录并刷新列表
- **删除全部**：列表上方「删除全部」按钮，确认后清空所有历史订单并显示删除条数

### 4.5 当前挂单（open-orders.html）

- **股东号**：必填，查询该股东号下未撤单订单
- 列表展示：订单编号、股东号、市场、股票、方向、价格、订单数量、已成交、剩余数量等

成交或撤单后，对应订单会从挂单列表移除（由 Core 的 TradeEventProcessor 与 OpenOrderStore 维护）。

### 4.6 行情查看（market.html）

- 填写 **市场 (market)**：XSHG / XSHE / BJSE  
- 填写 **股票代码 (securityId)**：如 600030  
- 点击「查询行情」后，会调用 Core 的 `/api/market-data` 接口，展示该股票的最新买价 (bidPrice) 和卖价 (askPrice)。  
- 行情数据由 Core 模块根据成交事件自动更新，无需在前端手动推送快照。

---

## 五、错误处理

### 常见问题

1. **CORS 跨域**
   - 现象：控制台报 CORS 相关错误
   - 处理：通过 HTTP 服务访问（如 `http://localhost:8000`），不要用 `file://` 直接打开 HTML

2. **服务连接失败**
   - 现象：概览显示服务异常或接口加载失败
   - 处理：确认 Admin(8080)、Core(8081) 已启动，并检查 `js/config.js` 中的地址

3. **股东号不存在**
   - 现象：下单报错（如 1009）
   - 处理：先在用户管理创建用户，确保股东号存在且为启用状态

4. **对敲风控**
   - 现象：下单报错 1002（对敲交易）
   - 处理：同一股东号同市场同证券不能同时有未成交的买与卖，需先撤单或等成交后再下单

### 错误码简表

| 错误码 | 含义       |
|--------|------------|
| 1001   | 无效订单   |
| 1002   | 对敲交易   |
| 1003   | 资金不足   |
| 1004   | 订单不存在 |
| 1005   | 无效证券   |
| 1009   | 股东号无效或已禁用 |

更多见后端模块文档。

---

## 六、技术说明

- **技术栈**：HTML5、CSS3、原生 JavaScript（ES6+），无前端框架
- **请求**：Fetch API，封装在 `js/api.js`
- **数据格式**：请求/响应均为 JSON，Content-Type: application/json

---

## 七、参考文档

- 后端 Admin 模块：`docs/admin模块功能与接口说明.md`
- 后端 Core 模块：`docs/core模块功能与接口说明.md`
- 项目使用说明：`docs/项目使用说明.md`
- 功能改进说明：`docs/功能改进说明.md`
- 前端故障排查：`trading-frontend/TROUBLESHOOTING.md`
