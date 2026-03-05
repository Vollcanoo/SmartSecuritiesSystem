package com.trading.protocol;

/**
 * 网关与客户端之间的消息类型（网关转发到 Core 时仍使用 trading-common 的 Request/Response）
 */
public enum GatewayMessageType {
    ORDER,
    CANCEL
}
