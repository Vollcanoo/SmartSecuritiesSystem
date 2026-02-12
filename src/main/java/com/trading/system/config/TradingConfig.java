package com.trading.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "trading") // 对应 yaml 里的 trading 层级
public class TradingConfig {

    private Gateway gateway = new Gateway();
    private Exchange exchange = new Exchange();

    @Data
    public static class Gateway {
        private int tcpPort; // 自动对应 yaml 里的 tcp-port
    }

    @Data
    public static class Exchange {
        private int simDelay; // 自动对应 yaml 里的 sim-delay
    }
}
