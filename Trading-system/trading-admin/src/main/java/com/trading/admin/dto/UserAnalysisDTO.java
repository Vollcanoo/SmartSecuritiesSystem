package com.trading.admin.dto;

import java.math.BigDecimal;

public class UserAnalysisDTO {

    private Long totalOrders;
    private Long filledOrders;
    private Long cancelledOrders;
    private BigDecimal totalTurnover;
    private BigDecimal avgPrice;

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
}