package com.trading.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易引擎相关配置（不修改引擎代码，仅本模块使用）
 */
@Component
@ConfigurationProperties(prefix = "trading.engine")
public class EngineConfig {

    /** 价格精度：业务价格 * PRICE_SCALE = 引擎内部 long 价格 */
    private int priceScale = 10000;

    /** 预置交易对：market:securityId，引擎启动时注册为 symbolId 1,2,3... */
    private List<String> symbols = new ArrayList<>();

    public int getPriceScale() {
        return priceScale;
    }

    public void setPriceScale(int priceScale) {
        this.priceScale = priceScale;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }
}
