package com.trading.core.controller;

import com.trading.core.service.OrderService;
import com.trading.core.service.PerformanceMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 性能监控 API：查看实时指标、执行基准测试。
 */
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {

    private final PerformanceMetricsService metricsService;
    private final OrderService orderService;

    public PerformanceController(PerformanceMetricsService metricsService, OrderService orderService) {
        this.metricsService = metricsService;
        this.orderService = orderService;
    }

    /**
     * 获取实时性能指标（订单数/成交数/延迟分布/吞吐量）。
     * GET /api/performance
     */
    @GetMapping
    public ResponseEntity<?> getMetrics() {
        return ResponseEntity.ok(Map.of("code", 0, "message", "success", "data", metricsService.getMetrics()));
    }

    /**
     * 执行基准压测：批量提交订单，返回吞吐量和延迟。
     * POST /api/performance/benchmark?count=1000
     */
    @PostMapping("/benchmark")
    public ResponseEntity<?> runBenchmark(@RequestParam(defaultValue = "1000") int count) {
        if (count < 1 || count > 100000) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "count 范围: 1-100000"));
        }
        Map<String, Object> result = metricsService.runBenchmark(count, orderService);
        return ResponseEntity.ok(Map.of("code", 0, "message", "benchmark complete", "data", result));
    }

    /**
     * 重置性能统计数据。
     * POST /api/performance/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<?> reset() {
        metricsService.reset();
        return ResponseEntity.ok(Map.of("code", 0, "message", "性能统计已重置"));
    }
}
