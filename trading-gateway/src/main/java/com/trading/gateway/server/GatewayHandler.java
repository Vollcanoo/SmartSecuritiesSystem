package com.trading.gateway.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.model.request.CancelRequest;
import com.trading.common.model.request.OrderRequest;
import com.trading.gateway.client.CoreClient;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 解析一行 JSON：含 origClOrderId 则按撤单转发，否则按下单转发；将 Core 返回的 JSON 写回客户端。
 * 异常时返回统一格式 {"error":"CODE"}，不向客户端暴露内部信息。
 */
@Component
@ChannelHandler.Sharable
public class GatewayHandler extends SimpleChannelInboundHandler<String> {

    private static final Logger log = LoggerFactory.getLogger(GatewayHandler.class);
    private static final String ERROR_INVALID_JSON = "INVALID_JSON";
    private static final String ERROR_BAD_REQUEST = "BAD_REQUEST";
    private static final String ERROR_INTERNAL = "INTERNAL_ERROR";

    private final CoreClient coreClient;
    private final ObjectMapper objectMapper;

    public GatewayHandler(CoreClient coreClient, ObjectMapper objectMapper) {
        this.coreClient = coreClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        if (msg == null || msg.trim().isEmpty()) {
            return;
        }
        String line = msg.trim();
        try {
            JsonNode root = objectMapper.readTree(line);
            String response;
            if (root.has("origClOrderId")) {
                CancelRequest req = objectMapper.treeToValue(root, CancelRequest.class);
                Object result = coreClient.cancelOrder(req);
                response = result instanceof String ? (String) result : objectMapper.writeValueAsString(result);
            } else {
                OrderRequest req = objectMapper.treeToValue(root, OrderRequest.class);
                Object result = coreClient.placeOrder(req);
                response = result instanceof String ? (String) result : objectMapper.writeValueAsString(result);
            }
            ctx.writeAndFlush(response + "\n");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("Gateway invalid JSON: {}", line);
            writeError(ctx, ERROR_INVALID_JSON);
        } catch (Exception e) {
            log.warn("Gateway handler error", e);
            writeError(ctx, ERROR_BAD_REQUEST);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Gateway handler error", cause);
        writeError(ctx, ERROR_INTERNAL);
    }

    private void writeError(ChannelHandlerContext ctx, String errorCode) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("error", errorCode);
            ctx.writeAndFlush(objectMapper.writeValueAsString(body) + "\n");
        } catch (Exception e) {
            ctx.close();
        }
    }
}
