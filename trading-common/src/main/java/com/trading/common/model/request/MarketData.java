// trading-common/src/main/java/com/trading/common/model/request/MarketDataRequest.java
package com.trading.common.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MarketData {
    @JsonProperty("market")
    private String market;

    @JsonProperty("securityId")
    private String securityId;

    @JsonProperty("bidPrice")
    private Double bidPrice;

    @JsonProperty("askPrice")
    private Double askPrice;

    // Getter 和 Setter
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }

    public String getSecurityId() { return securityId; }
    public void setSecurityId(String securityId) { this.securityId = securityId; }

    public Double getBidPrice() { return bidPrice; }
    public void setBidPrice(Double bidPrice) { this.bidPrice = bidPrice; }

    public Double getAskPrice() { return askPrice; }
    public void setAskPrice(Double askPrice) { this.askPrice = askPrice; }

    @Override
    public String toString() {
        return "MarketDataRequest{" +
                "market='" + market + '\'' +
                ", securityId='" + securityId + '\'' +
                ", bidPrice=" + bidPrice +
                ", askPrice=" + askPrice +
                '}';
    }
}