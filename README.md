# ⚡ CyberTrader — 模拟股票交易对敲撮合系统

<div align="center">

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.14-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个基于 exchange-core 撮合引擎的 A 股模拟交易系统，支持对敲风控、实时行情、AI 数据分析。

</div>

---

## 📺 演示视频

<video src="https://github.com/user-attachments/assets/77b13702-3f2c-40cf-b48b-f195e92e8954" controls width="100%"></video>

---

## 功能概览

| 模块 | 功能 |
|------|------|
| 🔄 交易 | 买卖下单、价格量校验、订单确认 |
| 🛡️ 风控 | 对敲检测（同股东不能自成交）、涨跌停限价 |
| 💱 撮合 | 价格优先·时间优先，基于 exchange-core LMAX Disruptor |
| 📊 行情 | 对接新浪财经实时 A 股行情，自动刷新 |
| 🤖 AI 分析 | 接入 SiliconFlow LLM（Qwen / DeepSeek），分析交易数据 |
| 🖥️ 管理后台 | 用户管理、订单历史、挂单查询 |

---

## 快速启动

**环境要求**：JDK 17+、Maven 3.8+

```bash
# 1. 编译并启动所有服务
./start-all.sh

# 2. 打开前端（浏览器访问）
open http://localhost:8000
```

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:8000 |
| Admin API | http://localhost:8080 |
| Core API | http://localhost:8081 |
| Gateway TCP | localhost:9000 |

停止所有服务：

```bash
./stop-all.sh
```

---

## 技术栈

- **后端**：Java 17 · Spring Boot 2.7 · exchange-core 0.5.4（LMAX Disruptor）
- **数据库**：H2（内存，开箱即用）/ MySQL（可选）
- **行情**：新浪财经实时接口（免费，无需密钥）
- **AI**：[SiliconFlow](https://cloud.siliconflow.cn/) API（Qwen2.5 / DeepSeek / GLM-4）
- **前端**：原生 HTML + CSS + JS，极客暗色主题

---

## 模块结构

```
trading-system/
├── trading-admin/     # 管理后台（8080）：用户、历史订单、AI 分析
├── trading-core/      # 交易核心（8081）：撮合引擎、行情、风控
├── trading-gateway/   # 网关（9000）：TCP 接入
├── trading-common/    # 公共 DTO / 工具
├── trading-protocol/  # 通讯协议定义
├── trading-frontend/  # 前端静态页面
├── exchange-core/     # 撮合引擎源码（LMAX Disruptor）
├── start-all.sh       # 一键启动
└── stop-all.sh        # 一键停止
```

---

## AI 分析使用

1. 打开前端 → **AI 分析** 页
2. 在 SiliconFlow（https://cloud.siliconflow.cn/）注册并获取 API Key
3. 粘贴 API Key，选择模型，输入问题，点击 **ANALYZE**

支持模型：Qwen2.5-7B（免费）、Qwen2.5-72B、DeepSeek V3、DeepSeek R1、GLM-4、Llama 3.1

---

## 注意事项

- 本系统为**纯模拟**，不连接真实交易所，不涉及真实资金
- H2 为内存数据库，重启后数据清空；如需持久化请配置 MySQL
- 行情数据来自新浪财经公开接口，仅供学习参考，延迟约 15 秒

---

## License

[MIT](LICENSE) © 2026 CyberTrader
