// trading-common/src/main/java/com/trading/common/model/request/CancelRequest.java
package com.trading.common.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CancelRequest {
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

    @Override
    public String toString() {
        return "CancelRequest{" +
                "clOrderId='" + clOrderId + '\'' +
                ", origClOrderId='" + origClOrderId + '\'' +
                ", market='" + market + '\'' +
                ", securityId='" + securityId + '\'' +
                ", shareholderId='" + shareholderId + '\'' +
                ", side='" + side + '\'' +
                '}';
    }
}