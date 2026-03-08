// trading-core/src/main/java/com/trading/core/TradingCoreApplication.java
package com.trading.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TradingCoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(TradingCoreApplication.class, args);
    }
}