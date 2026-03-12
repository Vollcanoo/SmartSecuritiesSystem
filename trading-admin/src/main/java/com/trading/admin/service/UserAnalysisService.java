package com.trading.admin.service;

import com.trading.admin.dto.UserAnalysisDTO;
import com.trading.admin.entity.OrderHistory;
import com.trading.admin.entity.User;
import com.trading.admin.repository.OrderHistoryRepository;
import com.trading.admin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserAnalysisService {

    private final OrderHistoryRepository orderHistoryRepository;
    private final UserRepository userRepository;

    public UserAnalysisService(OrderHistoryRepository orderHistoryRepository,
                               UserRepository userRepository) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.userRepository = userRepository;
    }

    public UserAnalysisDTO analyze(Long userId,
                                   LocalDateTime start,
                                   LocalDateTime end) {

        User user = userRepository.findByUid(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在, uid=" + userId));

        String shareholderId = user.getShareholderId();

        List<OrderHistory> orders =
                orderHistoryRepository.findByShareholderIdAndCreatedAtBetween(shareholderId, start, end);

        UserAnalysisDTO dto = new UserAnalysisDTO();

        // 汇总统计
        long total = orders.size();
        long filledOrders = orders.stream()
                .filter(o -> {
                    String status = o.getStatus();
                    return "FILLED".equals(status) || "PARTIALLY_FILLED".equals(status);
                })
                .count();

        long cancelled = orders.stream()
                .filter(o -> "CANCELLED".equals(o.getStatus()))
                .count();

        BigDecimal turnover = orders.stream()
                .filter(o -> o.getFilledQty() != null && o.getFilledQty() > 0)
                .map(o -> {
                    double price = o.getPrice() != null ? o.getPrice() : 0.0d;
                    int qty = o.getFilledQty() != null ? o.getFilledQty() : 0;
                    return BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalFilledQty = orders.stream()
                .filter(o -> o.getFilledQty() != null)
                .mapToLong(o -> o.getFilledQty())
                .sum();

        BigDecimal avgPrice;
        if (totalFilledQty == 0L) {
            avgPrice = BigDecimal.ZERO;
        } else {
            avgPrice = turnover.divide(BigDecimal.valueOf(totalFilledQty), 4, RoundingMode.HALF_UP);
        }

        dto.setTotalOrders(total);
        dto.setFilledOrders(filledOrders);
        dto.setCancelledOrders(cancelled);
        dto.setTotalTurnover(turnover);
        dto.setAvgPrice(avgPrice);

        // 按日期聚合，供前端画每日订单数 / 成交金额折线图、柱状图
        Map<LocalDate, DailyAggregation> dailyMap = new LinkedHashMap<>();
        for (OrderHistory order : orders) {
            LocalDateTime createdAt = order.getCreatedAt();
            if (createdAt == null) {
                continue;
            }
            LocalDate day = createdAt.toLocalDate();
            DailyAggregation agg = dailyMap.computeIfAbsent(day, d -> new DailyAggregation());

            agg.totalOrders++;
            String status = order.getStatus();
            if ("FILLED".equals(status) || "PARTIALLY_FILLED".equals(status)) {
                agg.filledOrders++;
            }
            if ("CANCELLED".equals(status)) {
                agg.cancelledOrders++;
            }

            Integer filledQty = order.getFilledQty();
            if (filledQty != null && filledQty > 0) {
                double price = order.getPrice() != null ? order.getPrice() : 0.0d;
                BigDecimal amount = BigDecimal.valueOf(price)
                        .multiply(BigDecimal.valueOf(filledQty));
                agg.totalTurnover = agg.totalTurnover.add(amount);

                // 按买卖方向拆分为收入 / 支出
                String side = order.getSide();
                if ("S".equalsIgnoreCase(side)) {
                    // 卖出视为收入
                    agg.totalIncome = agg.totalIncome.add(amount);
                } else if ("B".equalsIgnoreCase(side)) {
                    // 买入视为支出
                    agg.totalExpense = agg.totalExpense.add(amount);
                }
            }
        }

        List<UserAnalysisDTO.DailyStat> dailyStats = new ArrayList<>();
        dailyMap.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    LocalDate day = entry.getKey();
                    DailyAggregation agg = entry.getValue();

                    UserAnalysisDTO.DailyStat stat = new UserAnalysisDTO.DailyStat();
                    stat.setDate(day.toString());
                    stat.setTotalOrders(agg.totalOrders);
                    stat.setFilledOrders(agg.filledOrders);
                    stat.setCancelledOrders(agg.cancelledOrders);
                    stat.setTotalTurnover(agg.totalTurnover);
                    stat.setTotalIncome(agg.totalIncome);
                    stat.setTotalExpense(agg.totalExpense);

                    dailyStats.add(stat);
                });

        dto.setDailyStats(dailyStats);

        return dto;
    }

    /**
     * 内部使用的每日聚合对象，用于构建 DTO 所需的 DailyStat 列表。
     */
    private static class DailyAggregation {
        private long totalOrders = 0L;
        private long filledOrders = 0L;
        private long cancelledOrders = 0L;
        private BigDecimal totalTurnover = BigDecimal.ZERO;
        private BigDecimal totalIncome = BigDecimal.ZERO;
        private BigDecimal totalExpense = BigDecimal.ZERO;
    }
}