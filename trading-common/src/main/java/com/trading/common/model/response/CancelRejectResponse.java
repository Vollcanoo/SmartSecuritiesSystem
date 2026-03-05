// trading-common/src/main/java/com/trading/common/model/response/CancelRejectResponse.java
package com.trading.common.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 撤单非法回报
 */

public class CancelRejectResponse {
    @JsonProperty("clOrderId")
    private String clOrderId;

    @JsonProperty("origClOrderId")
    private String origClOrderId;

    @JsonProperty("rejectCode")
    private Integer rejectCode;

    @JsonProperty("rejectText")
    private String rejectText;

    // Getter 和 Setter
    public String getClOrderId() { return clOrderId; }
    public void setClOrderId(String clOrderId) { this.clOrderId = clOrderId; }

    public String getOrigClOrderId() { return origClOrderId; }
    public void setOrigClOrderId(String origClOrderId) { this.origClOrderId = origClOrderId; }

    public Integer getRejectCode() { return rejectCode; }
    public void setRejectCode(Integer rejectCode) { this.rejectCode = rejectCode; }

    public String getRejectText() { return rejectText; }
    public void setRejectText(String rejectText) { this.rejectText = rejectText; }
}