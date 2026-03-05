// trading-common/src/main/java/com/trading/common/enums/Side.java
package com.trading.common.enums;

public enum Side {
    BUY("B", "买入"),
    SELL("S", "卖出");

    private final String code;
    private final String description;

    Side(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static Side fromCode(String code) {
        return "B".equals(code) ? BUY : SELL;
    }
}