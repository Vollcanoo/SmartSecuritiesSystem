# 快速入门指南

本指南帮助你在 **5 分钟内**启动并运行交易系统。

## 🎯 目标

完成本指南后，你将能够：
- ✅ 启动所有系统服务
- ✅ 创建用户账号
- ✅ 通过前端下单
- ✅ 查看订单历史

---

## ⚡ 快速开始（5分钟）

### Step 1: 检查环境（1分钟）

```bash
# 检查 Java（必须是 17）
java -version
# 应该显示：openjdk version "17.x.x"

# 如果没有 Java 17，安装它：
# macOS:
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17  # Apple Silicon
# 或
export JAVA_HOME=/usr/local/opt/openjdk@17     # Intel Mac
```

### Step 2: 安装依赖（2分钟）

```bash
# 1. 安装 Maven（如果没有）
brew install maven

# 2. 安装 MySQL（如果没有）
brew install mysql
brew services start mysql

# 3. 创建数据库
mysql -u root -p
# 输入密码后执行：
CREATE DATABASE trading_admin;
exit
```

### Step 3: 启动服务（1分钟）

```bash
cd Trading-system

# 一键启动所有服务
./start-all.sh

# 等待所有服务启动完成...
```

**注意**：首次启动需要下载依赖，可能需要几分钟。

### Step 4: 启动前端（30秒）

```bash
# 新开一个终端
cd Trading-system/trading-frontend
./start-server.sh

# 或手动启动：
python3 -m http.server 8000
```

### Step 5: 访问系统（30秒）

打开浏览器，访问：http://localhost:8000

🎉 **完成！** 系统已经运行了！

---

## 🔥 第一次使用

### 1️⃣ 创建用户

1. 在前端点击 **"用户管理"**
2. 输入用户名，点击 **"创建用户"**
3. 系统会自动生成股东号（SH00010001 格式）

**命令行方式**：
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"我的第一个用户"}'
```

### 2️⃣ 下单交易

1. 点击 **"交易下单"**
2. 填写订单信息：
   - 市场：XSHG（沪市）
   - 股票代码：600030
   - 买卖方向：买入（B）
   - 数量：100
   - 价格：10.5
   - 股东号：选择刚创建的股东号
3. 点击 **"提交订单"**

**命令行方式**：
```bash
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "market": "XSHG",
    "securityId": "600030",
    "side": "B",
    "qty": 100,
    "price": 10.5,
    "shareholderId": "SH00010001"
  }'
```

### 3️⃣ 查看订单

1. 点击 **"订单历史"**
2. 可以看到刚才的订单

或者查看当前挂单：
1. 点击 **"当前挂单"**
2. 输入股东号查询

---

## 📖 核心概念（3分钟读完）

### 订单类型

| 字段 | 说明 | 示例 |
|------|------|------|
| market | 市场代码 | XSHG（沪市）、XSHE（深市）、BJSE（北交所） |
| securityId | 股票代码 | 600030、000001 |
| side | 买卖方向 | B（买）、S（卖） |
| qty | 数量 | 100（必须 > 0） |
| price | 价格 | 10.5（必须 > 0） |
| shareholderId | 股东号 | SH00010001 |

### 订单状态

- **ORDER_ACK**: 订单确认（已接受）
- **ORDER_REJECT**: 订单拒绝（未通过校验）
- **CANCEL_ACK**: 撤单成功
- **CANCEL_REJECT**: 撤单失败
- **FILLED**: 完全成交
- **PARTIALLY_FILLED**: 部分成交

### 拒绝原因码

| 代码 | 含义 | 说明 |
|------|------|------|
| 1001 | 交易非法 | 基本校验失败（市场、价格、数量等） |
| 1002 | 对敲 | 同股东同股票已有反向挂单 |
| 1003 | 撤单非法 | 订单不存在或已成交 |
| 1009 | 股东号非法 | 股东号不存在或已禁用 |

### 对敲规则

**什么是对敲？**
同一个股东号，对同一只股票，同时挂买单和卖单（自己和自己交易）。

**系统如何防止？**
- 如果已有买单，再下卖单 → 拒绝（rejectCode=1002）
- 如果已有卖单，再下买单 → 拒绝（rejectCode=1002）

**解决方法**：
1. 先撤销原订单，再下新订单
2. 使用不同股东号下单

---

## 🎮 常用操作

### 下单

```bash
# 买单
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "market": "XSHG",
    "securityId": "600030",
    "side": "B",
    "qty": 100,
    "price": 10.5,
    "shareholderId": "SH00010001"
  }'
```

### 撤单

```bash
# 只需要原订单号
curl -X POST http://localhost:8081/api/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "origClOrderId": "BUY001",
    "shareholderId": "SH00010001"
  }'
```

### 查询挂单

```bash
curl "http://localhost:8081/api/orders?shareholderId=SH00010001"
```

### 查询历史

```bash
curl "http://localhost:8080/api/orders/history?shareholderId=SH00010001"
```

---

## 🧪 测试撮合

创建两个用户，模拟真实交易：

```bash
# 1. 创建用户A
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"用户A"}'
# 假设得到股东号：SH00010001

# 2. 创建用户B
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"用户B"}'
# 假设得到股东号：SH00010002

# 3. 用户A下买单
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "market": "XSHG",
    "securityId": "600030",
    "side": "B",
    "qty": 100,
    "price": 10.5,
    "shareholderId": "SH00010001"
  }'

# 4. 用户B下卖单（价格匹配，会成交！）
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "market": "XSHG",
    "securityId": "600030",
    "side": "S",
    "qty": 100,
    "price": 10.5,
    "shareholderId": "SH00010002"
  }'

# 5. 查看订单历史（可以看到成交记录）
curl "http://localhost:8080/api/orders/history?shareholderId=SH00010001"
```

---

## 🛑 停止服务

```bash
# 一键停止
./stop-all.sh

# 或手动停止
ps aux | grep trading
kill -9 <PID>
```

---

## ❓ 常见问题

### Q1: 端口被占用怎么办？

```bash
# 查找占用端口的进程
lsof -i :8080
lsof -i :8081
lsof -i :9000

# 杀掉进程
kill -9 <PID>
```

### Q2: MySQL 连接失败？

```bash
# 检查 MySQL 是否运行
brew services list | grep mysql

# 启动 MySQL
brew services start mysql

# 检查数据库是否存在
mysql -u root -p -e "SHOW DATABASES;"
```

### Q3: 启动失败，提示找不到 exchange-core？

需要先安装 exchange-core：
```bash
cd /path/to/exchange-core
mvn clean install -DskipTests
```

### Q4: Java 版本不对？

系统要求 JDK 17：
```bash
# macOS 安装
brew install openjdk@17

# 设置环境变量
export JAVA_HOME=/opt/homebrew/opt/openjdk@17  # Apple Silicon
export PATH="$JAVA_HOME/bin:$PATH"

# 验证
java -version
```

### Q5: 前端无法访问后端？

1. 确认后端已启动
2. 检查配置文件：`trading-frontend/js/config.js`
3. 使用 HTTP Server 启动前端（不要直接打开 HTML）

---

## 📚 下一步

- 📖 阅读完整文档：[README.md](README.md)
- 🧪 运行自动化测试：`./test-system.sh`
- 🎨 自定义前端界面
- 🔧 添加新功能

---

## 💡 提示

### 订单号可以不填

系统会自动生成订单号，格式：
- 下单：ORD + 时间戳 + 序号
- 撤单：CAN + 时间戳 + 序号

```bash
# 不指定订单号
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "market": "XSHG",
    "securityId": "600030",
    "side": "B",
    "qty": 100,
    "price": 10.5,
    "shareholderId": "SH00010001"
  }'
```

### 撤单只需要原订单号

```bash
# 最简单的撤单方式
curl -X POST http://localhost:8081/api/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "origClOrderId": "ORD123",
    "shareholderId": "SH00010001"
  }'
```

---

## 🎯 检查清单

启动系统前，确认：
- [ ] JDK 17 已安装
- [ ] Maven 已安装
- [ ] MySQL 已安装并运行
- [ ] 数据库 `trading_admin` 已创建
- [ ] exchange-core 已安装到本地 Maven

功能测试：
- [ ] 可以创建用户
- [ ] 可以下单
- [ ] 可以撤单
- [ ] 可以查询订单
- [ ] 对敲风控正常工作

---

## 🚀 性能提示

- 系统默认配置适合开发和测试
- 生产环境建议调整 JVM 参数
- 可以使用 H2 数据库替代 MySQL（开发环境）

---

**🎉 恭喜！你已经掌握了系统的基本使用！**

如有问题，请查看：
- [README.md](README.md) - 完整文档
- [docs/](docs/) - 详细说明文档
- [TROUBLESHOOTING.md](trading-frontend/TROUBLESHOOTING.md) - 故障排除

---

<div align="center">
Made with ❤️ by Elvis Wang
</div>
