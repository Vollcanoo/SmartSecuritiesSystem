package com.trading.core.model;

import java.util.Objects;

/**
 * 业务层维护的「未成交/未完全撤单」订单快照，按股东号可查。
 */
public class OpenOrderRecord {

    private String clOrderId;
    private String shareholderId;
    private String market;
    private String securityId;
    private String side;
    private Double price;
    private Integer orderQty;
    private Integer filledQty;
    private Long engineOrderId;

    public String getClOrderId() { return clOrderId; }
    public void setClOrderId(String clOrderId) { this.clOrderId = clOrderId; }
    public String getShareholderId() { return shareholderId; }
    public void setShareholderId(String shareholderId) { this.shareholderId = shareholderId; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getSecurityId() { return securityId; }
    public void setSecurityId(String securityId) { this.securityId = securityId; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getOrderQty() { return orderQty; }
    public void setOrderQty(Integer orderQty) { this.orderQty = orderQty; }
    public Integer getFilledQty() { return filledQty; }
    public void setFilledQty(Integer filledQty) { this.filledQty = filledQty; }
    public Long getEngineOrderId() { return engineOrderId; }
    public void setEngineOrderId(Long engineOrderId) { this.engineOrderId = engineOrderId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OpenOrderRecord that = (OpenOrderRecord) o;
        return Objects.equals(clOrderId, that.clOrderId);
    }
    @Override
    public int hashCode() { return Objects.hash(clOrderId); }
}
