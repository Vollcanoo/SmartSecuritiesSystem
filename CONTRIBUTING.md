# 贡献指南 (CONTRIBUTING)

感谢你对本项目感兴趣！我们欢迎任何形式的贡献。

---

## 🎯 贡献方式

你可以通过以下方式为项目做出贡献：

- 🐛 **报告 Bug**：发现问题？提交 Issue
- ✨ **提出新功能**：有好想法？我们想听听
- 📝 **改进文档**：文档不够清晰？帮我们完善
- 💻 **提交代码**：修复 Bug 或实现新功能
- 📖 **分享经验**：写教程、博客文章
- ⭐ **点个 Star**：这对我们很重要！

---

## 📋 开始之前

### 阅读文档

- [README.md](README.md) - 项目总览
- [QUICKSTART.md](QUICKSTART.md) - 快速开始
- [docs/](docs/) - 详细文档

### 环境准备

确保你的开发环境满足要求：
- JDK 17
- Maven 3.6+
- MySQL 5.7/8.x
- Git

---

## 🐛 报告 Bug

### 提交 Issue 前

1. **搜索现有 Issue**：你的问题可能已经被报告了
2. **确认是 Bug**：不是使用问题或配置错误
3. **尝试最新版本**：Bug 可能已被修复

### 提交 Issue

请包含以下信息：

**Bug 描述**
清晰简洁地描述 Bug。

**复现步骤**
1. 启动服务 '...'
2. 调用 API '...'
3. 查看错误 '...'

**预期行为**
应该发生什么？

**实际行为**
实际发生了什么？

**环境信息**
- OS: [e.g. macOS 13.0]
- Java: [e.g. OpenJDK 17.0.2]
- Maven: [e.g. 3.8.6]
- MySQL: [e.g. 8.0.33]

**日志/截图**
如果可能，附上相关日志或截图。

**示例**：
```markdown
## Bug 描述
下单接口返回 500 错误

## 复现步骤
1. 启动所有服务
2. 执行：curl -X POST http://localhost:8081/api/order -d '...'
3. 返回 500 Internal Server Error

## 预期行为
应该返回 ORDER_ACK 或 ORDER_REJECT

## 实际行为
返回 500 错误

## 环境
- OS: macOS 13.0
- Java: OpenJDK 17.0.2
- Maven: 3.8.6

## 日志
```
java.lang.NullPointerException at ...
```
```

---

## ✨ 提出新功能

### 提交 Feature Request

描述你想要的功能：

**功能描述**
清晰描述你想要什么功能。

**使用场景**
为什么需要这个功能？解决什么问题？

**解决方案**
你认为应该如何实现？

**替代方案**
有没有其他解决方法？

**额外信息**
任何其他相关信息。

---

## 💻 提交代码

### Fork & Clone

```bash
# 1. Fork 本仓库到你的账号

# 2. Clone 你的 Fork
git clone https://github.com/your-username/Trading-system.git
cd Trading-system

# 3. 添加上游仓库
git remote add upstream https://github.com/original-repo/Trading-system.git
```

### 创建分支

```bash
# 从 main 创建新分支
git checkout -b feature/your-feature-name

# 或修复 Bug
git checkout -b fix/bug-description
```

**分支命名规范**：
- `feature/xxx` - 新功能
- `fix/xxx` - Bug 修复
- `docs/xxx` - 文档改进
- `refactor/xxx` - 代码重构
- `test/xxx` - 测试相关

### 开发

1. **编写代码**
   - 遵循项目代码风格
   - 添加必要的注释
   - 保持代码简洁

2. **编写测试**
   ```bash
   # 运行测试确保没有破坏现有功能
   mvn test
   ```

3. **更新文档**
   - 如果添加了新功能，更新 README.md
   - 如果修改了 API，更新 API 文档

### 提交代码

```bash
# 添加改动
git add .

# 提交（清晰的提交信息）
git commit -m "feat: 添加市价单功能"
```

**提交信息规范**：
- `feat:` - 新功能
- `fix:` - Bug 修复
- `docs:` - 文档更新
- `style:` - 代码格式（不影响功能）
- `refactor:` - 重构
- `test:` - 测试相关
- `chore:` - 构建/工具相关

**示例**：
```bash
feat: 添加市价单支持
fix: 修复对敲风控逻辑错误
docs: 更新 API 文档
refactor: 重构订单校验逻辑
test: 添加撮合引擎单元测试
```

### 推送 & 创建 PR

```bash
# 推送到你的 Fork
git push origin feature/your-feature-name

# 在 GitHub 上创建 Pull Request
```

### Pull Request 规范

**标题**：清晰描述改动

**描述**：
```markdown
## 改动说明
描述你做了什么改动

## 相关 Issue
Closes #123

## 改动类型
- [ ] Bug 修复
- [ ] 新功能
- [ ] 文档更新
- [ ] 代码重构
- [ ] 测试

## 测试
描述如何测试你的改动

## 检查清单
- [ ] 代码遵循项目风格
- [ ] 添加了必要的注释
- [ ] 更新了相关文档
- [ ] 添加了测试
- [ ] 所有测试通过
- [ ] 没有引入新的警告
```

---

## 📝 代码规范

### Java 代码风格

```java
// 1. 使用 Lombok 简化代码
@Data
@Builder
public class Order {
    private String clOrderId;
    private String market;
    // ...
}

// 2. 使用 @Slf4j 记录日志
@Slf4j
@Service
public class OrderService {
    public void placeOrder(OrderRequest req) {
        log.info("Placing order: {}", req.getClOrderId());
        // ...
    }
}

// 3. 异常处理
try {
    // ...
} catch (Exception e) {
    log.error("Failed to place order", e);
    throw new OrderException("下单失败", e);
}

// 4. 注释
/**
 * 下单接口
 * 
 * @param request 订单请求
 * @return 订单确认或拒绝响应
 */
public Response placeOrder(OrderRequest request) {
    // ...
}
```

### 命名规范

- **类名**：大驼峰 `OrderService`
- **方法名**：小驼峰 `placeOrder`
- **变量名**：小驼峰 `clOrderId`
- **常量**：大写下划线 `MAX_ORDER_SIZE`
- **包名**：全小写 `com.trading.core.service`

### REST API 规范

```
GET    /api/orders       - 查询列表
GET    /api/orders/{id}  - 查询单个
POST   /api/orders       - 创建
PUT    /api/orders/{id}  - 更新
DELETE /api/orders/{id}  - 删除
```

---

## 🧪 测试规范

### 单元测试

```java
@SpringBootTest
class OrderServiceTest {
    
    @Autowired
    private OrderService orderService;
    
    @Test
    void testPlaceOrder_Success() {
        // Given
        OrderRequest request = OrderRequest.builder()
            .market("XSHG")
            .securityId("600030")
            .side("B")
            .qty(100)
            .price(10.5)
            .shareholderId("SH00010001")
            .build();
        
        // When
        Response response = orderService.placeOrder(request);
        
        // Then
        assertNotNull(response);
        assertEquals("ORDER_ACK", response.getType());
    }
    
    @Test
    void testPlaceOrder_InvalidMarket() {
        // Given
        OrderRequest request = OrderRequest.builder()
            .market("INVALID")
            .build();
        
        // When & Then
        assertThrows(ValidationException.class, () -> {
            orderService.placeOrder(request);
        });
    }
}
```

### 集成测试

```bash
# 运行集成测试脚本
./test-system.sh
```

---

## 📖 文档规范

### Markdown 文档

- 使用中文撰写
- 使用清晰的标题层级
- 添加代码示例
- 使用表格整理信息
- 添加适当的表情符号

### API 文档

每个 API 接口应包含：
- 接口路径和方法
- 请求参数说明
- 请求示例
- 响应示例
- 错误码说明

---

## 🔄 同步上游

保持你的 Fork 与上游同步：

```bash
# 获取上游更新
git fetch upstream

# 切换到主分支
git checkout main

# 合并上游更新
git merge upstream/main

# 推送到你的 Fork
git push origin main
```

---

## ✅ PR Review 流程

提交 PR 后：

1. **自动检查**：CI/CD 会自动运行测试
2. **代码审查**：维护者会审查你的代码
3. **反馈修改**：根据反馈修改代码
4. **合并**：通过审查后会被合并

---

## 🎓 学习资源

### 项目相关

- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [exchange-core 文档](https://github.com/exchange-core/exchange-core)
- [Netty 文档](https://netty.io/wiki/)

### Git & GitHub

- [Git 教程](https://git-scm.com/book/zh/v2)
- [GitHub Flow](https://guides.github.com/introduction/flow/)
- [如何写好 Commit Message](https://www.conventionalcommits.org/)

---

## 💬 讨论与交流

- **Issue**：技术问题、Bug 报告
- **Discussions**：功能讨论、想法交流
- **Email**：敏感问题或私密讨论

---

## 🏆 贡献者

感谢所有为本项目做出贡献的人！

<!-- 这里可以使用 all-contributors 来展示贡献者 -->

---

## 📜 行为准则

### 我们的承诺

为了营造开放友好的环境，我们承诺：

- 尊重不同观点和经验
- 接受建设性批评
- 关注对社区最有利的事情
- 对其他社区成员表示同理心

### 不可接受的行为

- 使用性化语言或图像
- 人身攻击或侮辱性评论
- 骚扰（公开或私下）
- 未经许可发布他人隐私信息
- 其他不专业或不受欢迎的行为

### 执行

如果观察到不当行为，请联系项目维护者。

---

## ❓ 常见问题

### Q: 我不会写代码，可以贡献吗？

当然可以！你可以：
- 改进文档
- 报告 Bug
- 提出功能建议
- 帮助其他用户解决问题

### Q: 我的 PR 多久会被审查？

通常在 1-3 个工作日内会收到反馈。

### Q: PR 被拒绝了怎么办？

不要灰心！查看反馈意见，修改后可以重新提交。

### Q: 可以同时提交多个 PR 吗？

可以，但建议每个 PR 只解决一个问题或添加一个功能。

---

## 📞 联系我们

- 项目地址：https://github.com/your-username/Trading-system
- Issue 提交：https://github.com/your-username/Trading-system/issues
- Email: your-email@example.com

---

<div align="center">

**感谢你的贡献！** ❤️

让我们一起打造更好的交易系统！

</div>
