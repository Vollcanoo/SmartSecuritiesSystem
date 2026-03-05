package com.trading.admin.controller;

import com.trading.admin.dto.OrderEventDto;
import com.trading.admin.service.OrderHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 接收 trading-core 推送的订单事件，落库维护历史订单状态。
 * 若配置了 trading.admin.internal-token，请求头须带 X-Internal-Token 且一致才可调用。
 */
@RestController
@RequestMapping("/internal")
public class InternalOrderEventController {

    private static final Logger log = LoggerFactory.getLogger(InternalOrderEventController.class);

    @Value("${trading.admin.internal-token:}")
    private String internalToken;

    private final OrderHistoryService orderHistoryService;

    public InternalOrderEventController(OrderHistoryService orderHistoryService) {
        this.orderHistoryService = orderHistoryService;
    }

    @PostMapping("/order-events")
    public ResponseEntity<Void> onOrderEvent(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody OrderEventDto event) {
        if (internalToken != null && !internalToken.isBlank()) {
            if (token == null || !internalToken.equals(token)) {
                log.warn("Internal order-events rejected: missing or invalid token");
                return ResponseEntity.status(401).build();
            }
        }
        if (event == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            orderHistoryService.processEvent(event);
            if (log.isDebugEnabled()) {
                log.debug("Order event processed: clOrderId={}, eventType={}", event.getClOrderId(), event.getEventType());
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.warn("Process order event failed: {}", event.getClOrderId(), e);
            return ResponseEntity.status(500).build();
        }
    }
}
