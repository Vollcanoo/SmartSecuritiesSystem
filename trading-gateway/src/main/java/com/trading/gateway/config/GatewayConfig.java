package com.trading.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 网关配置：监听端口、Core 地址
 */
@Component
@ConfigurationProperties(prefix = "trading.gateway")
public class GatewayConfig {

    /** 客户端连接端口（Netty） */
    private int port = 9000;

    /** 交易核心服务地址，用于转发订单/撤单 */
    private String coreBaseUrl = "http://localhost:8081";

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCoreBaseUrl() {
        return coreBaseUrl;
    }

    public void setCoreBaseUrl(String coreBaseUrl) {
        this.coreBaseUrl = coreBaseUrl;
    }
}
