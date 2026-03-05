package com.trading.gateway.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.common.model.request.CancelRequest;
import com.trading.common.model.request.OrderRequest;
import com.trading.gateway.config.GatewayConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 调用 trading-core 的 REST API，不修改 Core 与引擎代码。
 */
@Component
public class CoreClient {

    private static final Logger log = LoggerFactory.getLogger(CoreClient.class);

    private final GatewayConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CoreClient(GatewayConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    public Object placeOrder(OrderRequest request) {
        String url = config.getCoreBaseUrl() + "/api/order";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return resp.getBody();
        } catch (Exception e) {
            log.warn("Core placeOrder failed: {}", e.getMessage());
            throw new RuntimeException("Core unavailable: " + e.getMessage());
        }
    }

    public Object cancelOrder(CancelRequest request) {
        String url = config.getCoreBaseUrl() + "/api/cancel";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<CancelRequest> entity = new HttpEntity<>(request, headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return resp.getBody();
        } catch (Exception e) {
            log.warn("Core cancelOrder failed: {}", e.getMessage());
            throw new RuntimeException("Core unavailable: " + e.getMessage());
        }
    }
}
