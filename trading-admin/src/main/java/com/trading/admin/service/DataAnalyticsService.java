package com.trading.admin.service;

import com.trading.admin.entity.OrderHistory;
import com.trading.admin.repository.OrderHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据分析服务：基于历史订单数据提供统计报表。
 * <p>
 * - 整体交易统计（总订单数、成交量、撤单率等）
 * - 按证券分组统计（最活跃品种、价格区间等）
 * - 按股东分组统计（各交易员贡献、盈亏分析等）
 * - 按时间维度统计
 */
@Service
public class DataAnalyticsService {

    private final OrderHistoryRepository repository;

    public DataAnalyticsService(OrderHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 整体交易统计摘要。
     */
    public Map<String, Object> getOverallStats() {
        List<OrderHistory> all = repository.findAll();
        Map<String, Object> stats = new LinkedHashMap<>();

        stats.put("totalOrders", all.size());

        long liveCount = all.stream().filter(o -> "LIVE".equals(o.getStatus())).count();
        long partialCount = all.stream().filter(o -> "PARTIALLY_FILLED".equals(o.getStatus())).count();
        long filledCount = all.stream().filter(o -> "FILLED".equals(o.getStatus())).count();
        long cancelledCount = all.stream().filter(o -> "CANCELLED".equals(o.getStatus())).count();

        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        statusBreakdown.put("LIVE", liveCount);
        statusBreakdown.put("PARTIALLY_FILLED", partialCount);
        statusBreakdown.put("FILLED", filledCount);
        statusBreakdown.put("CANCELLED", cancelledCount);
        stats.put("statusBreakdown", statusBreakdown);

        // 成交率
        double fillRate = all.isEmpty() ? 0 : (filledCount + partialCount) * 100.0 / all.size();
        stats.put("fillRate", Math.round(fillRate * 100) / 100.0);

        // 撤单率
        double cancelRate = all.isEmpty() ? 0 : cancelledCount * 100.0 / all.size();
        stats.put("cancelRate", Math.round(cancelRate * 100) / 100.0);

        // 总成交量
        long totalFilledQty = all.stream()
                .filter(o -> o.getFilledQty() != null)
                .mapToLong(OrderHistory::getFilledQty)
                .sum();
        stats.put("totalFilledQty", totalFilledQty);

        // 总成交额（估算 = sum(filledQty * price)）
        double totalTurnover = all.stream()
                .filter(o -> o.getFilledQty() != null && o.getFilledQty() > 0 && o.getPrice() != null)
                .mapToDouble(o -> o.getFilledQty() * o.getPrice())
                .sum();
        stats.put("totalTurnover", Math.round(totalTurnover * 100) / 100.0);

        // 买卖方向统计
        long buyCount = all.stream().filter(o -> "B".equals(o.getSide())).count();
        long sellCount = all.stream().filter(o -> "S".equals(o.getSide())).count();
        stats.put("buyOrders", buyCount);
        stats.put("sellOrders", sellCount);

        // 活跃股东数
        long activeShareholders = all.stream()
                .map(OrderHistory::getShareholderId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        stats.put("activeShareholders", activeShareholders);

        // 活跃证券数
        long activeSecurities = all.stream()
                .map(o -> o.getMarket() + ":" + o.getSecurityId())
                .distinct()
                .count();
        stats.put("activeSecurities", activeSecurities);

        return stats;
    }

    /**
     * 按证券分组统计。
     */
    public List<Map<String, Object>> getSecurityStats() {
        List<OrderHistory> all = repository.findAll();

        Map<String, List<OrderHistory>> grouped = all.stream()
                .collect(Collectors.groupingBy(o ->
                        (o.getMarket() != null ? o.getMarket() : "N/A") + ":" +
                        (o.getSecurityId() != null ? o.getSecurityId() : "N/A")));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<OrderHistory>> entry : grouped.entrySet()) {
            String key = entry.getKey();
            List<OrderHistory> orders = entry.getValue();
            String[] parts = key.split(":", 2);

            Map<String, Object> sec = new LinkedHashMap<>();
            sec.put("market", parts[0]);
            sec.put("securityId", parts.length > 1 ? parts[1] : "N/A");
            sec.put("totalOrders", orders.size());

            // 成交量
            long filledQty = orders.stream()
                    .filter(o -> o.getFilledQty() != null)
                    .mapToLong(OrderHistory::getFilledQty)
                    .sum();
            sec.put("totalFilledQty", filledQty);

            // 价格区间
            DoubleSummaryStatistics priceStats = orders.stream()
                    .filter(o -> o.getPrice() != null && o.getPrice() > 0)
                    .mapToDouble(OrderHistory::getPrice)
                    .summaryStatistics();
            if (priceStats.getCount() > 0) {
                sec.put("minPrice", priceStats.getMin());
                sec.put("maxPrice", priceStats.getMax());
                sec.put("avgPrice", Math.round(priceStats.getAverage() * 100) / 100.0);
            }

            // 状态分布
            Map<String, Long> statusDist = orders.stream()
                    .collect(Collectors.groupingBy(o -> o.getStatus() != null ? o.getStatus() : "UNKNOWN", Collectors.counting()));
            sec.put("statusDistribution", statusDist);

            // 成交额
            double turnover = orders.stream()
                    .filter(o -> o.getFilledQty() != null && o.getFilledQty() > 0 && o.getPrice() != null)
                    .mapToDouble(o -> o.getFilledQty() * o.getPrice())
                    .sum();
            sec.put("turnover", Math.round(turnover * 100) / 100.0);

            result.add(sec);
        }

        // 按成交量降序排列
        result.sort((a, b) -> Long.compare(
                (Long) b.getOrDefault("totalFilledQty", 0L),
                (Long) a.getOrDefault("totalFilledQty", 0L)));

        return result;
    }

    /**
     * 按股东分组统计。
     */
    public List<Map<String, Object>> getShareholderStats() {
        List<OrderHistory> all = repository.findAll();

        Map<String, List<OrderHistory>> grouped = all.stream()
                .filter(o -> o.getShareholderId() != null)
                .collect(Collectors.groupingBy(OrderHistory::getShareholderId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<OrderHistory>> entry : grouped.entrySet()) {
            List<OrderHistory> orders = entry.getValue();

            Map<String, Object> sh = new LinkedHashMap<>();
            sh.put("shareholderId", entry.getKey());
            sh.put("totalOrders", orders.size());

            long buyOrders = orders.stream().filter(o -> "B".equals(o.getSide())).count();
            long sellOrders = orders.stream().filter(o -> "S".equals(o.getSide())).count();
            sh.put("buyOrders", buyOrders);
            sh.put("sellOrders", sellOrders);

            long filledQty = orders.stream()
                    .filter(o -> o.getFilledQty() != null)
                    .mapToLong(OrderHistory::getFilledQty)
                    .sum();
            sh.put("totalFilledQty", filledQty);

            // 成交额
            double turnover = orders.stream()
                    .filter(o -> o.getFilledQty() != null && o.getFilledQty() > 0 && o.getPrice() != null)
                    .mapToDouble(o -> o.getFilledQty() * o.getPrice())
                    .sum();
            sh.put("turnover", Math.round(turnover * 100) / 100.0);

            // 撤单数
            long cancelled = orders.stream().filter(o -> "CANCELLED".equals(o.getStatus())).count();
            sh.put("cancelledOrders", cancelled);

            // 完全成交数
            long filled = orders.stream().filter(o -> "FILLED".equals(o.getStatus())).count();
            sh.put("filledOrders", filled);

            // 活跃证券数
            long securities = orders.stream()
                    .map(o -> o.getMarket() + ":" + o.getSecurityId())
                    .distinct()
                    .count();
            sh.put("activeSecurities", securities);

            result.add(sh);
        }

        // 按订单数降序
        result.sort((a, b) -> Integer.compare(
                (Integer) b.getOrDefault("totalOrders", 0),
                (Integer) a.getOrDefault("totalOrders", 0)));

        return result;
    }

    /**
     * 综合分析报告。
     */
    public Map<String, Object> getFullReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("overview", getOverallStats());
        report.put("bySecurityId", getSecurityStats());
        report.put("byShareholder", getShareholderStats());
        report.put("generatedAt", java.time.LocalDateTime.now().toString());
        return report;
    }
}
