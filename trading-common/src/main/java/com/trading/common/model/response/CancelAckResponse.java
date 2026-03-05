// trading-common/src/main/java/com/trading/common/model/response/CancelAckResponse.java
package com.trading.common.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CancelAckResponse {
    @JsonProperty("clOrderId")
    private String clOrderId;

    @JsonProperty("origClOrderId")
    private String origClOrderId;

    @JsonProperty("market")
    private String market;

    @JsonProperty("securityId")
    private String securityId;

    @JsonProperty("shareholderId")
    private String shareholderId;

    @JsonProperty("side")
    private String side;

    @JsonProperty("qty")
    private Integer qty;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("cumQty")
    private Integer cumQty;

    @JsonProperty("canceledQty")
    private Integer canceledQty;

    // Getter 和 Setter
    public String getClOrderId() { return clOrderId; }
    public void setClOrderId(String clOrderId) { this.clOrderId = clOrderId; }

    public String getOrigClOrderId() { return origClOrderId; }
    public void setOrigClOrderId(String origClOrderId) { this.origClOrderId = origClOrderId; }

    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }

    public String getSecurityId() { return securityId; }
    public void setSecurityId(String securityId) { this.securityId = securityId; }

    public String getShareholderId() { return shareholderId; }
    public void setShareholderId(String shareholderId) { this.shareholderId = shareholderId; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Integer getCumQty() { return cumQty; }
    public void setCumQty(Integer cumQty) { this.cumQty = cumQty; }

    public Integer getCanceledQty() { return canceledQty; }
    public void setCanceledQty(Integer canceledQty) { this.canceledQty = canceledQty; }
}