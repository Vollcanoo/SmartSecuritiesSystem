# 开发环境配置示例

# ===========================================
# 数据库配置
# ===========================================

# MySQL 配置（trading-admin 使用）
# 文件位置: trading-admin/src/main/resources/application.yaml
DB_HOST=localhost
DB_PORT=3306
DB_NAME=trading_admin
DB_USER=root
DB_PASSWORD=your_password_here

# 或者直接修改 application.yaml：
# spring:
#   datasource:
#     url: jdbc:mysql://localhost:3306/trading_admin
#     username: root
#     password: your_password_here

# ===========================================
# JDK 环境变量
# ===========================================

# macOS (Apple Silicon)
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"

# macOS (Intel)
# export JAVA_HOME=/usr/local/opt/openjdk@17
# export PATH="$JAVA_HOME/bin:$PATH"

# Linux
# export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
# export PATH="$JAVA_HOME/bin:$PATH"

# Windows
# set JAVA_HOME=C:\Program Files\Java\jdk-17
# set PATH=%JAVA_HOME%\bin;%PATH%

# ===========================================
# 服务端口配置
# ===========================================

# trading-admin
ADMIN_PORT=8080

# trading-core
CORE_PORT=8081

# trading-gateway
GATEWAY_TCP_PORT=9000
GATEWAY_HTTP_PORT=8082

# 前端
FRONTEND_PORT=8000

# ===========================================
# 服务间调用配置
# ===========================================

# Core 调用 Admin 的地址（在 trading-core/src/main/resources/application.yaml）
ADMIN_API_URL=http://localhost:8080

# Gateway 调用 Core 的地址（在 trading-gateway/src/main/resources/application.yaml）
CORE_API_URL=http://localhost:8081

# ===========================================
# 内部 Token 配置（可选）
# ===========================================

# Admin 内部接口 Token（用于 Core 推送订单事件）
# 在 trading-admin/src/main/resources/application.yaml 中设置：
# trading:
#   admin:
#     internal-token: your-secret-token-here

# 在 trading-core/src/main/resources/application.yaml 中配置相同 Token：
# trading:
#   core:
#     admin-url: http://localhost:8080
#     internal-token: your-secret-token-here

# ===========================================
# JVM 参数配置
# ===========================================

# trading-admin JVM 参数
ADMIN_JVM_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"

# trading-core JVM 参数（exchange-core 需要）
CORE_JVM_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED"

# trading-gateway JVM 参数
GATEWAY_JVM_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# ===========================================
# Maven 配置
# ===========================================

# Maven 镜像（可选，加速下载）
# 在 ~/.m2/settings.xml 中配置：
# <mirrors>
#   <mirror>
#     <id>aliyun</id>
#     <mirrorOf>central</mirrorOf>
#     <name>Aliyun Maven</name>
#     <url>https://maven.aliyun.com/repository/public</url>
#   </mirror>
# </mirrors>

# ===========================================
# 日志配置
# ===========================================

# 日志级别（在各模块的 application.yaml 中配置）
LOG_LEVEL=INFO

# 日志文件路径
LOG_PATH=./logs

# ===========================================
# exchange-core 配置
# ===========================================

# exchange-core 本地安装路径
EXCHANGE_CORE_PATH=/path/to/exchange-core

# 安装命令
# cd $EXCHANGE_CORE_PATH
# mvn clean install -DskipTests

# ===========================================
# 开发工具配置
# ===========================================

# IntelliJ IDEA
# 1. 导入项目：File -> Open -> 选择项目根目录
# 2. 等待 Maven 下载依赖
# 3. 设置 JDK 17：File -> Project Structure -> Project SDK
# 4. 运行配置：Run -> Edit Configurations -> Add New -> Spring Boot

# VS Code
# 1. 安装插件：Java Extension Pack, Spring Boot Extension Pack
# 2. 打开项目文件夹
# 3. 等待 Java 项目加载完成
# 4. F5 运行/调试

# ===========================================
# 远程调试配置
# ===========================================

# 启动时添加调试参数：
DEBUG_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"

# 使用方式：
# mvn spring-boot:run -Dspring-boot.run.jvmArguments="$DEBUG_OPTS"

# IDE 连接：
# - IntelliJ IDEA: Run -> Edit Configurations -> Add New -> Remote JVM Debug
#   - Host: localhost
#   - Port: 5005

# ===========================================
# 测试配置
# ===========================================

# 跳过测试编译
MVN_SKIP_TESTS="-DskipTests"

# 运行特定测试
# mvn test -Dtest=OrderServiceTest

# ===========================================
# 性能调优参数
# ===========================================

# 堆内存设置
HEAP_OPTS="-Xms1g -Xmx4g"

# GC 设置
GC_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# GC 日志
GC_LOG_OPTS="-Xlog:gc*:file=gc.log:time,level,tags"

# ===========================================
# Docker 配置（可选）
# ===========================================

# MySQL Docker 启动
# docker run -d \
#   --name trading-mysql \
#   -e MYSQL_ROOT_PASSWORD=your_password \
#   -e MYSQL_DATABASE=trading_admin \
#   -p 3306:3306 \
#   mysql:8.0

# ===========================================
# 环境变量配置文件
# ===========================================

# 创建 .env 文件在项目根目录：
# DB_PASSWORD=your_password
# ADMIN_PORT=8080
# CORE_PORT=8081
# GATEWAY_TCP_PORT=9000

# 然后在启动脚本中加载：
# source .env
