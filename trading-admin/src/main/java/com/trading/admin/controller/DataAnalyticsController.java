package com.trading.admin.controller;

import com.trading.admin.service.DataAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据分析 API：提供交易统计报表和分析功能。
 * <p>
 * 所有接口返回基于历史订单数据的实时聚合分析结果。
 */
@RestController
@RequestMapping("/api/analytics")
public class DataAnalyticsController {

    private final DataAnalyticsService analyticsService;

    public DataAnalyticsController(DataAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * 整体交易统计摘要。
     * GET /api/analytics/overview
     *
     * 返回：总订单数、状态分布、成交率、撤单率、总成交量/额、活跃股东/证券数
     */
    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(Map.of("code", 0, "message", "success", "data", analyticsService.getOverallStats()));
    }

    /**
     * 按证券分组统计。
     * GET /api/analytics/securities
     *
     * 返回：每个证券的订单数、成交量、价格区间、成交额、状态分布
     */
    @GetMapping("/securities")
    public ResponseEntity<?> securities() {
        return ResponseEntity.ok(Map.of("code", 0, "message", "success", "data", analyticsService.getSecurityStats()));
    }

    /**
     * 按股东分组统计。
     * GET /api/analytics/shareholders
     *
     * 返回：每个交易员的订单数、买卖方向、成交量/额、撤单数、活跃证券数
     */
    @GetMapping("/shareholders")
    public ResponseEntity<?> shareholders() {
        return ResponseEntity.ok(Map.of("code", 0, "message", "success", "data", analyticsService.getShareholderStats()));
    }

    /**
     * 综合分析报告（包含所有维度）。
     * GET /api/analytics/report
     *
     * 返回：overview + bySecurityId + byShareholder 的完整报告
     */
    @GetMapping("/report")
    public ResponseEntity<?> report() {
        return ResponseEntity.ok(Map.of("code", 0, "message", "success", "data", analyticsService.getFullReport()));
    }
}
