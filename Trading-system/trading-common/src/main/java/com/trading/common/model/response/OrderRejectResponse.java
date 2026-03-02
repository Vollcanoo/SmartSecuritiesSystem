// trading-common/src/main/java/com/trading/common/model/response/OrderRejectResponse.java
package com.trading.common.model.response;

/*
  订单非法回报
 */

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderRejectResponse {
    @JsonProperty("clOrderId")
    private String clOrderId;

    @JsonProperty("market")
    private String market;

    @JsonProperty("securityId")
    private String securityId;

    @JsonProperty("side")
    private String side;

    @JsonProperty("qty")
    private Integer qty;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("shareholderId")
    private String shareholderId;

    @JsonProperty("rejectCode")
    private Integer rejectCode;

    @JsonProperty("rejectText")
    private String rejectText;

    // Getter 和 Setter
    public String getClOrderId() { return clOrderId; }
    public void setClOrderId(String clOrderId) { this.clOrderId = clOrderId; }

    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }

    public String getSecurityId() { return securityId; }
    public void setSecurityId(String securityId) { this.securityId = securityId; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public String getShareholderId() { return shareholderId; }
    public void setShareholderId(String shareholderId) { this.shareholderId = shareholderId; }

    public Integer getRejectCode() { return rejectCode; }
    public void setRejectCode(Integer rejectCode) { this.rejectCode = rejectCode; }

    public String getRejectText() { return rejectText; }
    public void setRejectText(String rejectText) { this.rejectText = rejectText; }
}