package com.trading.core.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 向 Admin 推送订单事件，用于维护历史订单表。
 * 若未配置 baseUrl 或推送失败，仅打日志不影响下单/撤单结果。
 */
@Component
public class OrderEventSender {

    private static final Logger log = LoggerFactory.getLogger(OrderEventSender.class);

    @Value("${trading.admin.base-url:}")
    private String adminBaseUrl;

    @Value("${trading.admin.internal-token:}")
    private String internalToken;

    private final RestTemplate restTemplate;

    public OrderEventSender(@Qualifier("adminRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Async("eventSenderExecutor")
    public void sendEvent(Map<String, Object> event) {
        if (adminBaseUrl == null || adminBaseUrl.isBlank()) return;
        String url = adminBaseUrl.replaceAll("/$", "") + "/internal/order-events";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (internalToken != null && !internalToken.isBlank()) {
                headers.set("X-Internal-Token", internalToken);
            }
            HttpEntity<Map<String, Object>> req = new HttpEntity<>(event, headers);
            restTemplate.postForObject(url, req, Void.class);
        } catch (Exception e) {
            log.warn("Send order event to admin failed: {} {}", url, e.getMessage());
        }
    }
}
