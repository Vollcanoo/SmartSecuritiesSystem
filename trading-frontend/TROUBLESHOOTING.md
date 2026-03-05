# 前端问题排查指南

## 问题：服务状态显示异常，查询不到用户信息

### 可能原因与解决方案

#### 1. CORS 跨域问题

**现象**：浏览器控制台显示 CORS 相关错误，如：
```
Access to fetch at 'http://localhost:8080/api/users' from origin 'null' has been blocked by CORS policy
```

**原因**：直接打开 HTML 文件（file:// 协议）时，浏览器会阻止跨域请求。

**解决方案**：
- 使用 HTTP 服务器方式启动前端（推荐）：
  ```bash
  cd trading-frontend
  python3 -m http.server 8000
  ```
  然后访问 `http://localhost:8000`

- 或者在浏览器中禁用 CORS（仅用于开发测试）：
  - Chrome: `chrome --disable-web-security --user-data-dir=/tmp/chrome`
  - 不推荐用于生产环境

#### 2. 后端服务未启动

**现象**：前端显示"无法连接服务"或网络错误。

**检查方法**：
```bash
# 检查 Admin 服务
curl http://localhost:8080/actuator/health

# 检查 Core 服务
curl http://localhost:8081/actuator/health
```

**解决方案**：
- 确保后端服务已启动：
  ```bash
  ./start-all.sh
  ```
- 检查端口是否被占用：
  ```bash
  lsof -i :8080
  lsof -i :8081
  ```

#### 3. API 地址配置错误

**现象**：前端无法连接到后端 API。

**检查方法**：
- 打开浏览器开发者工具（F12）
- 查看"网络"（Network）标签页
- 检查请求的 URL 是否正确

**解决方案**：
- 检查 `js/config.js` 中的 API 地址：
  ```javascript
  const CONFIG = {
      ADMIN_BASE_URL: 'http://localhost:8080',
      CORE_BASE_URL: 'http://localhost:8081'
  };
  ```
- 如果后端部署在不同地址，修改上述配置

#### 4. 健康检查接口响应格式问题

**现象**：服务状态显示异常，但后端服务实际正常运行。

**原因**：健康检查接口返回 `{"status":"UP"}`，前端代码可能无法正确解析。

**解决方案**：
- 已修复：健康检查接口现在使用独立的处理逻辑
- 如果仍有问题，检查浏览器控制台的错误信息

#### 5. 用户查询接口响应格式问题

**现象**：用户列表为空或显示"-"。

**检查方法**：
```bash
# 直接调用 API 检查响应
curl http://localhost:8080/api/users
```

**预期响应**：
```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 1,
      "uid": 10001,
      "shareholderId": "A123456789",
      "username": "user1",
      "status": 1,
      ...
    }
  ]
}
```

**解决方案**：
- 如果 API 返回正常但前端显示异常，检查浏览器控制台的错误信息
- 确保响应格式符合预期（code=0, data 为数组）

#### 6. 浏览器控制台错误

**排查步骤**：
1. 打开浏览器开发者工具（F12）
2. 查看"控制台"（Console）标签页
3. 查看"网络"（Network）标签页
4. 检查失败的请求和错误信息

**常见错误**：
- `Failed to fetch`: 网络连接问题或 CORS 问题
- `Unexpected token`: JSON 解析错误，可能是响应格式不正确
- `404 Not Found`: API 路径错误或服务未启动

### 调试技巧

1. **启用详细日志**：
   - 打开浏览器控制台
   - 查看 `console.log` 和 `console.error` 输出

2. **检查网络请求**：
   - 打开"网络"标签页
   - 查看每个 API 请求的状态码和响应内容
   - 检查请求头是否正确

3. **测试 API 直接调用**：
   ```bash
   # 测试健康检查
   curl http://localhost:8080/actuator/health
   
   # 测试用户查询
   curl http://localhost:8080/api/users
   
   # 测试下单
   curl -X POST http://localhost:8081/api/order \
     -H "Content-Type: application/json" \
     -d '{"clOrderId":"test1","market":"XSHG","securityId":"600030","side":"B","qty":100,"price":10.5,"shareholderId":"A123456789"}'
   ```

### 快速修复检查清单

- [ ] 后端服务已启动（Admin 8080, Core 8081）
- [ ] 使用 HTTP 服务器方式启动前端（不是直接打开 HTML）
- [ ] 检查 `js/config.js` 中的 API 地址是否正确
- [ ] 检查浏览器控制台是否有错误信息
- [ ] 检查网络请求是否成功（状态码 200）
- [ ] 检查 API 响应格式是否符合预期

### 如果问题仍未解决

1. **查看浏览器控制台完整错误信息**
2. **检查后端服务日志**：
   ```bash
   tail -f logs-admin.log
   tail -f logs-core.log
   ```
3. **确认 API 响应格式**：直接调用 API 检查响应内容
4. **检查防火墙/代理设置**：确保浏览器能访问 localhost:8080 和 8081
