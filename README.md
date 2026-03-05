# Smart Securities System (Trading System)

本项目是一个基于高性能内存撮合引擎 **exchange-core2** 开发的证券交易原型系统。它采用微服务架构思想，集成了高并发接入网关、核心撮合引擎、管理后台以及响应式前端界面。

## 核心架构设计

系统由以下五个主要模块组成：

1.  **trading-gateway (接入网关)**:
    *   基于 **Netty** 实现的高性能 TCP 服务端。
    *   监听 **9000** 端口，接收按行分隔的 JSON 交易命令。
    *   负责协议解析并将请求转发至核心层。
2.  **trading-core (业务核心)**:
    *   整个系统的“大脑”，持有 **exchange-core2** 撮合引擎实例。
    *   负责订单的前置风控校验（自成交检查、股东校验等）。
    *   包含 `TradeEventProcessor` 异步处理引擎回写的成交回报。
3.  **trading-admin (管理后台)**:
    *   基于 Spring Boot 和 JPA 的管理模块。
    *   监听 **8080** 端口，负责订单历史持久化、用户/股东管理。
    *   使用 MySQL 存储数据。
4.  **trading-frontend (前端界面)**:
    *   纯 HTML/JS 实现的交易终端。
    *   提供交易下单、撤单、委托查询和历史成交查看。
5.  **trading-common / trading-protocol**:
    *   定义了通用的枚举（Side, Market）和网络通信协议格式。

## 技术栈

*   **后端**: Java 17, Spring Boot 2.7, Netty 4.1, Maven 3.x
*   **引擎**: [exchange-core2](https://github.com/mzheravin/exchange-core) (高性能低延迟撮合引擎)
*   **数据库**: MySQL 8.0
*   **前端**: HTML5, Vanilla JavaScript, CSS3, Python (用于启动静态服务器)

## 快速启动指南

### 1. 环境准备
*   安装 **JDK 17** (必须是 17，因为引擎依赖此版本)。
*   安装 **Maven**。
*   安装 **MySQL** 并创建数据库：
    ```sql
    CREATE DATABASE trading_admin DEFAULT CHARACTER SET utf8mb4;
    ```
*   安装 **Python** (用于启动前端服务)。

### 2. 预安装撮合引擎
由于 `exchange-core` 是外部高性能依赖，请确保已将其安装到本地 Maven 仓库：
1.  进入 `exchange-core` 目录。
2.  运行 `mvn clean install -DskipTests`。

### 3. 一键启动
在项目根目录下，直接双击运行：
*   **`start-all.bat`**: 自动完成编译、打包、并按顺序启动 Admin、Core、Gateway 以及 Frontend 服务。

### 4. 访问系统
*   **交易前端**: [http://localhost:8000](http://localhost:8000)
*   **管理后台 API**: [http://localhost:8080](http://localhost:8080)
*   **TCP 交易网关**: `localhost:9000`

### 5. 停止服务
*   运行 **`stop-all.bat`** 即可安全关闭所有相关 Java 进程和前端服务器。

## 主要业务流程

1.  **委托投放**: 前端 -> 网关(TCP) -> Core(下单/风控) -> 引擎(撮合)。
2.  **成交回报**: 引擎 -> Core(事件处理器) -> Admin(存库) -> 前端(展示)。
3.  **风控校验**: 系统在下单前会自动检查资金、持仓、自成交（对敲）以及股东有效性。

## 常见问题
*   **乱码问题**: 启动脚本已强制开启 UTF-8 (chcp 65001)，确保在 Windows CMD 下显示正常。
*   **启动顺序**: 必须先启动 Admin（数据库连接），再启动 Core 和 Gateway。
*   **Python 服务器**: 如果 Python 启动失败，请检查环境变量中是 `python` 还是 `python3`。

---
*本项目仅用于学习和原型开发展示。*
