package com.trading.core.controller;

import com.trading.common.model.request.CancelRequest;
import com.trading.common.model.request.OrderRequest;
import com.trading.core.model.OpenOrderRecord;
import com.trading.core.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 交易核心订单 API，供 Gateway 或直接调用。
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/order")
    public ResponseEntity<?> placeOrder(@RequestBody OrderRequest request) {
        Object result = orderService.placeOrder(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancelOrder(@RequestBody CancelRequest request) {
        Object result = orderService.cancelOrder(request);
        return ResponseEntity.ok(result);
    }

    /**
     * 查询某股东号下的当前挂单（未撤单的订单）。
     * 例：GET /api/orders?shareholderId=A123456789
     */
    @GetMapping("/orders")
    public ResponseEntity<List<OpenOrderRecord>> getOpenOrders(@RequestParam String shareholderId) {
        List<OpenOrderRecord> list = orderService.getOpenOrders(shareholderId);
        return ResponseEntity.ok(list);
    }
}
