package com.trading.admin.dto;

/**
 * Core 推送的订单事件体（内部接口）。
 */
public class OrderEventDto {

    /** PLACED=下单成功, CANCELLED=撤单成功, FILLED=全部成交, PARTIALLY_FILLED=部分成交 */
    private String eventType;
    private String clOrderId;
    private Long engineOrderId;
    private String shareholderId;
    private String market;
    private String securityId;
    private String side;
    private Double price;
    private Integer orderQty;
    private Integer filledQty;
    private Integer canceledQty;

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getClOrderId() { return clOrderId; }
    public void setClOrderId(String clOrderId) { this.clOrderId = clOrderId; }
    public Long getEngineOrderId() { return engineOrderId; }
    public void setEngineOrderId(Long engineOrderId) { this.engineOrderId = engineOrderId; }
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
    public Integer getCanceledQty() { return canceledQty; }
    public void setCanceledQty(Integer canceledQty) { this.canceledQty = canceledQty; }
}
