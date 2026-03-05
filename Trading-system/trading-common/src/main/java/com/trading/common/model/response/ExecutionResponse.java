// trading-common/src/main/java/com/trading/common/model/response/ExecutionResponse.java
package com.trading.common.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecutionResponse {
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

    @JsonProperty("execId")
    private String execId;

    @JsonProperty("execQty")
    private Integer execQty;

    @JsonProperty("execPrice")
    private Double execPrice;

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

    public String getExecId() { return execId; }
    public void setExecId(String execId) { this.execId = execId; }

    public Integer getExecQty() { return execQty; }
    public void setExecQty(Integer execQty) { this.execQty = execQty; }

    public Double getExecPrice() { return execPrice; }
    public void setExecPrice(Double execPrice) { this.execPrice = execPrice; }
}
