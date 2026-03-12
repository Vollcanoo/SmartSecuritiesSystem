package com.trading.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单个用户在指定时间区间内的交易分析结果。
 * 在原有汇总指标基础上，增加 dailyStats 便于前端绘制折线图/柱状图。
 */
public class UserAnalysisDTO {

    /** 订单总数 */
    private Long totalOrders;

    /** 成交订单数 */
    private Long filledOrders;

    /** 撤单订单数 */
    private Long cancelledOrders;

    /** 成交总金额 */
    private BigDecimal totalTurnover;

    /** 成交均价（按成交金额 / 成交数量） */
    private BigDecimal avgPrice;

    /** 按日期聚合的统计数据，供前端画每日订单数 / 成交金额等图表 */
    private List<DailyStat> dailyStats;

    public Long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(Long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public Long getFilledOrders() {
        return filledOrders;
    }

    public void setFilledOrders(Long filledOrders) {
        this.filledOrders = filledOrders;
    }

    public Long getCancelledOrders() {
        return cancelledOrders;
    }

    public void setCancelledOrders(Long cancelledOrders) {
        this.cancelledOrders = cancelledOrders;
    }

    public BigDecimal getTotalTurnover() {
        return totalTurnover;
    }

    public void setTotalTurnover(BigDecimal totalTurnover) {
        this.totalTurnover = totalTurnover;
    }

    public BigDecimal getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(BigDecimal avgPrice) {
        this.avgPrice = avgPrice;
    }

    public List<DailyStat> getDailyStats() {
        return dailyStats;
    }

    public void setDailyStats(List<DailyStat> dailyStats) {
        this.dailyStats = dailyStats;
    }

    /**
     * 单日聚合统计数据，用于前端折线图/柱状图。
     */
    public static class DailyStat {
        /** 日期字符串，格式例如 2026-03-07 */
        private String date;
        /** 当日订单总数 */
        private Long totalOrders;
        /** 当日成交订单数 */
        private Long filledOrders;
        /** 当日撤单订单数 */
        private Long cancelledOrders;
        /** 当日成交总金额 */
        private BigDecimal totalTurnover;
        /** 当日总收入（卖出成交金额之和） */
        private BigDecimal totalIncome;
        /** 当日总支出（买入成交金额之和） */
        private BigDecimal totalExpense;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public Long getTotalOrders() {
            return totalOrders;
        }

        public void setTotalOrders(Long totalOrders) {
            this.totalOrders = totalOrders;
        }

        public Long getFilledOrders() {
            return filledOrders;
        }

        public void setFilledOrders(Long filledOrders) {
            this.filledOrders = filledOrders;
        }

        public Long getCancelledOrders() {
            return cancelledOrders;
        }

        public void setCancelledOrders(Long cancelledOrders) {
            this.cancelledOrders = cancelledOrders;
        }

        public BigDecimal getTotalTurnover() {
            return totalTurnover;
        }

        public void setTotalTurnover(BigDecimal totalTurnover) {
            this.totalTurnover = totalTurnover;
        }

        public BigDecimal getTotalIncome() {
            return totalIncome;
        }

        public void setTotalIncome(BigDecimal totalIncome) {
            this.totalIncome = totalIncome;
        }

        public BigDecimal getTotalExpense() {
            return totalExpense;
        }

        public void setTotalExpense(BigDecimal totalExpense) {
            this.totalExpense = totalExpense;
        }
    }
}