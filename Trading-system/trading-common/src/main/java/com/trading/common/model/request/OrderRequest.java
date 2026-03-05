// trading-common/src/main/java/com/trading/common/model/request/OrderRequest.java
package com.trading.common.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderRequest {
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

    @Override
    public String toString() {
        return "OrderRequest{" +
                "clOrderId='" + clOrderId + '\'' +
                ", market='" + market + '\'' +
                ", securityId='" + securityId + '\'' +
                ", side='" + side + '\'' +
                ", qty=" + qty +
                ", price=" + price +
                ", shareholderId='" + shareholderId + '\'' +
                '}';
    }
}