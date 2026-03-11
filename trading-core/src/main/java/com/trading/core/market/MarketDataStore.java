package com.trading.core.market;

import com.trading.common.model.request.MarketData;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行情快照内存存储：按 (market, securityId) 维护最新一笔行情。
 */
@Component
public class MarketDataStore {

    private static final String SEP = "|";

    private final Map<String, MarketData> latestByKey = new ConcurrentHashMap<>();

    private String key(String market, String securityId) {
        String m = market == null ? "" : market;
        String s = securityId == null ? "" : securityId;
        return m + SEP + s;
    }

    public void update(MarketData md) {
        if (md == null) {
            return;
        }
        latestByKey.put(key(md.getMarket(), md.getSecurityId()), md);
    }

    public void batchUpdate(Iterable<MarketData> list) {
        if (list == null) {
            return;
        }
        for (MarketData md : list) {
            update(md);
        }
    }

    public Optional<MarketData> get(String market, String securityId) {
        return Optional.ofNullable(latestByKey.get(key(market, securityId)));
    }
}

