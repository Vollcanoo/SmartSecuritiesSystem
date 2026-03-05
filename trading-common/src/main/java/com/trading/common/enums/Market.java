// trading-common/src/main/java/com/trading/common/enums/Market.java
package com.trading.common.enums;

public enum Market {
    XSHG("上交所"),
    XSHE("深交所"),
    BJSE("北交所");

    private final String description;

    Market(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public static Market fromCode(String code) {
        for (Market market : values()) {
            if (market.name().equals(code)) {
                return market;
            }
        }
        throw new IllegalArgumentException("Invalid market code: " + code);
    }
}