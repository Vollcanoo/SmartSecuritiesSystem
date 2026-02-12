package com.trading.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class})
public class TradingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingSystemApplication.class, args);
    }

}
