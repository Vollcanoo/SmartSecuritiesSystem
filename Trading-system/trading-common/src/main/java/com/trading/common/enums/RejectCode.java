// trading-common/src/main/java/com/trading/common/enums/RejectCode.java
package com.trading.common.enums;

public enum RejectCode {
    INVALID_ORDER(1001, "无效订单"),
    SELF_MATCH(1002, "对敲交易"),
    INSUFFICIENT_FUNDS(1003, "资金不足"),
    ORDER_NOT_FOUND(1004, "订单不存在"),
    INVALID_SYMBOL(1005, "无效证券"),
    INVALID_MARKET(1006, "无效市场"),
    INVALID_PRICE(1007, "无效价格"),
    INVALID_QTY(1008, "无效数量"),
    INVALID_SHAREHOLDER_ID(1009, "无效股东号");

    private final Integer code;
    private final String description;

    RejectCode(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
