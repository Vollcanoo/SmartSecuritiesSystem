# 项目验证和使用指南

本指南帮助你快速验证系统是否正确安装和运行。

---

## ✅ 安装前检查清单

在开始之前，请确认以下内容：

```bash
# 1. 检查 Java 版本（必须是 17）
java -version
# 期望输出：openjdk version "17.x.x"

# 2. 检查 Maven（如未安装，参考 QUICKSTART.md）
mvn -version
# 期望输出：Apache Maven 3.x.x

# 3. 检查 MySQL（如未安装，参考 QUICKSTART.md）
mysql --version
# 期望输出：mysql Ver x.x.x

# 4. 检查 MySQL 服务
# macOS:
brew services list | grep mysql
# 或直接连接：
mysql -u root -p -e "SELECT 1"
```

---

## 🚀 一键安装和启动（推荐流程）

### Step 1: 安装缺失的依赖

```bash
# macOS 用户执行：
# 如果没有 Maven
brew install maven

# 如果没有 MySQL
brew install mysql
brew services start mysql

# 创建数据库
mysql -u root -p
> CREATE DATABASE trading_admin;
> exit
```

### Step 2: 配置数据库

编辑 `trading-admin/src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/trading_admin
    username: root
    password: your_password_here  # 修改这里
```

### Step 3: 安装 exchange-core（重要！）

```bash
# 如果还没有 exchange-core，需要先安装
# 假设你已经有 exchange-core 源码：
cd /path/to/exchange-core
mvn clean install -DskipTests

# 返回项目目录
cd /path/to/Trading-system
```

### Step 4: 编译项目

```bash
# 在项目根目录
mvn clean package -DskipTests
```

**期望输出**:
```
[INFO] BUILD SUCCESS
[INFO] Total time: XX s
```

### Step 5: 启动服务

```bash
# 一键启动
./start-all.sh
```

**期望输出**:
```
使用 JDK 17: /path/to/jdk-17
正在构建项目...
[INFO] BUILD SUCCESS

启动 trading-admin (端口 8080)...
等待 trading-admin 启动...
✓ trading-admin 已启动

启动 trading-core (端口 8081)...
等待 trading-core 启动...
✓ trading-core 已启动

启动 trading-gateway (端口 9000)...
等待 trading-gateway 启动...
✓ trading-gateway 已启动

========================================
    所有服务已成功启动！
========================================
```

### Step 6: 验证服务

```bash
# 快速验证所有服务
curl http://localhost:8080/actuator/health
# 期望：{"status":"UP"}

curl http://localhost:8081/actuator/health
# 期望：{"status":"UP"}

curl http://localhost:8082/actuator/health
# 期望：{"status":"UP"}
```

### Step 7: 启动前端

```bash
# 新开一个终端
cd trading-frontend
./start-server.sh

# 或手动：
python3 -m http.server 8000
```

**访问**: http://localhost:8000

---

## 🧪 功能验证（5分钟完整测试）

### 1️⃣ 创建测试用户

```bash
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"username":"测试用户A"}'
```

**期望输出**:
```json
{
  "uid": 10001,
  "shareholderId": "SH00010001",
  "username": "测试用户A",
  "status": 1,
  "createdAt": "2026-02-26T...",
  "updatedAt": "2026-02-26T..."
}
```

**保存股东号**：`SH00010001`

### 2️⃣ 测试下单

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

**期望输出**:
```json
{
  "type": "ORDER_ACK",
  "clOrderId": "ORD...",
  "market": "XSHG",
  "securityId": "600030",
  "side": "B",
  "qty": 100,
  "price": 10.5,
  "shareholderId": "SH00010001",
  "timestamp": ...
}
```

### 3️⃣ 测试对敲风控

```bash
# 尝试同一股东号下卖单（应被拒绝）
curl -X POST http://localhost:8081/api/order \
  -H "Content-Type: application/json" \
  -d '{
    "market": "XSHG",
    "securityId": "600030",
    "side": "S",
    "qty": 100,
    "price": 10.6,
    "shareholderId": "SH00010001"
  }'
```

**期望输出**:
```json
{
  "type": "ORDER_REJECT",
  "rejectCode": 1002,
  "rejectReason": "检测到对敲：同一股东号已有反向挂单",
  ...
}
```

✅ **如果看到 rejectCode=1002，说明对敲风控工作正常！**

### 4️⃣ 查询挂单

```bash
curl "http://localhost:8081/api/orders?shareholderId=SH00010001"
```

**期望输出**: 返回当前挂单列表

### 5️⃣ 测试撤单

```bash
# 假设上面下单的订单号是 ORD1708999200000001
curl -X POST http://localhost:8081/api/cancel \
  -H "Content-Type: application/json" \
  -d '{
    "origClOrderId": "你的订单号",
    "shareholderId": "SH00010001"
  }'
```

**期望输出**:
```json
{
  "type": "CANCEL_ACK",
  "clOrderId": "CAN...",
  "origClOrderId": "你的订单号",
  ...
}
```

---

## 🎯 自动化测试（推荐）

运行完整的自动化测试脚本：

```bash
./test-system.sh
```

**期望输出**:
```
========================================
           测试总结
========================================
总测试数: 19
通过测试: 19
失败测试: 0

========================================
    所有测试通过！系统运行正常！
========================================
```

---

## 🎨 前端验证

访问 http://localhost:8000

### 验证步骤：

1. **系统概览页**
   - ✅ 能看到服务状态（应该都是绿色✓）
   - ✅ 能看到用户统计

2. **用户管理页**
   - ✅ 点击"用户管理"
   - ✅ 能看到刚创建的用户
   - ✅ 尝试创建新用户
   - ✅ 尝试禁用/启用用户

3. **交易下单页**
   - ✅ 点击"交易下单"
   - ✅ 填写订单信息并提交
   - ✅ 能看到成功或失败提示

4. **订单历史页**
   - ✅ 点击"订单历史"
   - ✅ 能看到刚才的订单
   - ✅ 尝试删除订单

5. **当前挂单页**
   - ✅ 点击"当前挂单"
   - ✅ 输入股东号查询
   - ✅ 能看到未成交的订单

---

## 📊 性能验证（可选）

### 简单压测

```bash
# 准备测试数据文件
cat > order.json <<EOF
{"market":"XSHG","securityId":"600030","side":"B","qty":100,"price":10.5,"shareholderId":"SH00010001"}
EOF

# 使用 ab (Apache Bench) 压测
ab -n 100 -c 10 -p order.json -T 'application/json' \
   http://localhost:8081/api/order

# 查看结果
# Requests per second: XX [#/sec]  # TPS
# Time per request: XX [ms]        # 平均响应时间
```

---

## ❌ 常见问题排查

### 问题 1: 端口被占用

```bash
# 错误：Address already in use
# 解决：
lsof -i :8080  # 查找占用进程
kill -9 <PID>  # 杀掉进程
./start-all.sh # 重新启动
```

### 问题 2: MySQL 连接失败

```bash
# 错误：Communications link failure
# 检查：
brew services list | grep mysql
brew services start mysql

# 测试连接：
mysql -u root -p -e "SHOW DATABASES;"

# 确认数据库存在：
mysql -u root -p -e "SHOW DATABASES;" | grep trading_admin
```

### 问题 3: exchange-core 依赖找不到

```bash
# 错误：Could not find artifact exchange.core2:exchange-core
# 解决：
cd /path/to/exchange-core
mvn clean install -DskipTests
cd /path/to/Trading-system
mvn clean package -DskipTests
```

### 问题 4: Java 版本不对

```bash
# 错误：Unsupported class file major version
# 检查：
java -version

# 如果不是 17，安装并设置：
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17  # Apple Silicon
export PATH="$JAVA_HOME/bin:$PATH"
java -version  # 验证
```

### 问题 5: 前端无法访问后端

```bash
# 检查：
# 1. 确认后端已启动
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health

# 2. 检查配置
cat trading-frontend/js/config.js

# 3. 确保使用 HTTP Server 启动前端
cd trading-frontend
python3 -m http.server 8000
# 不要直接双击打开 HTML 文件
```

---

## 📋 验证检查清单

启动后验证：

- [ ] 所有服务健康检查通过
- [ ] 可以创建用户
- [ ] 可以下单
- [ ] 对敲风控工作正常
- [ ] 可以撤单
- [ ] 可以查询挂单
- [ ] 可以查询历史订单
- [ ] 前端可以访问
- [ ] 前端可以正常操作
- [ ] 自动化测试通过

**如果以上都完成，恭喜！系统已完全正常运行！** 🎉

---

## 🔄 停止服务

```bash
# 停止所有服务
./stop-all.sh

# 或手动停止
ps aux | grep trading
kill -9 <PID>

# 停止前端
# Ctrl+C 终止 python HTTP server
```

---

## 📖 下一步

### 学习路径

1. **新手**：
   - ✅ 已完成：验证系统运行
   - 📖 下一步：阅读 [QUICKSTART.md](QUICKSTART.md)
   - 🎨 然后：使用前端界面体验功能

2. **开发者**：
   - ✅ 已完成：验证系统运行
   - 📖 下一步：阅读 [README.md](README.md)
   - 💻 然后：查看源码，尝试修改功能
   - 📚 最后：阅读 [CONTRIBUTING.md](CONTRIBUTING.md)

3. **测试人员**：
   - ✅ 已完成：运行自动化测试
   - 🔧 下一步：导入 Postman 集合
   - 📊 然后：编写更多测试用例

---

## 💡 提示

### 日常使用

```bash
# 启动系统
./start-all.sh

# 查看日志
tail -f logs-admin.log
tail -f logs-core.log
tail -f logs-gateway.log

# 停止系统
./stop-all.sh
```

### 开发调试

```bash
# 单独启动某个模块（带远程调试）
cd trading-core
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

# IDE 连接到 localhost:5005 进行调试
```

### 性能监控

```bash
# 使用 JConsole 监控
jconsole

# 或使用 VisualVM
jvisualvm

# 连接到 Java 进程
```

---

## 📞 获取帮助

如果遇到问题：

1. 查看 [QUICKSTART.md](QUICKSTART.md) 的常见问题
2. 查看 [README.md](README.md) 的详细说明
3. 运行 `./test-system.sh` 诊断问题
4. 查看日志文件：`logs-*.log`
5. 提交 Issue 到项目仓库

---

<div align="center">

**系统验证完成！** ✅

开始享受交易系统吧！

如有问题，随时查看文档或提交 Issue。

</div>
