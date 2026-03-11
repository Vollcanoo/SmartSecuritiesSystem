package com.trading.core.market;

import com.trading.common.model.request.MarketData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 行情接入与查询接口：
 * - 内部 POST /internal/market-data/snapshot 批量更新行情
 * - 外部 GET  /api/market-data            查询单只股票最新行情
 */
@RestController
public class MarketDataController {

    private final MarketDataStore store;

    public MarketDataController(MarketDataStore store) {
        this.store = store;
    }

    /**
     * 批量更新行情快照。
     * 用于脚本/离线回放推送行情。
     */
    @PostMapping("/internal/market-data/snapshot")
    public ResponseEntity<Void> snapshot(@RequestBody List<MarketData> list) {
        store.batchUpdate(list);
        return ResponseEntity.ok().build();
    }

    /**
     * 查询单只股票最新行情。
     * 例：GET /api/market-data?market=XSHG&securityId=600030
     * 返回副本，避免并发修改导致前端收到 null。
     */
    @GetMapping("/api/market-data")
    public ResponseEntity<MarketData> getLatest(
            @RequestParam String market,
            @RequestParam String securityId
    ) {
        return store.get(market, securityId)
                .map(this::copy)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private MarketData copy(MarketData src) {
        MarketData d = new MarketData();
        d.setMarket(src.getMarket());
        d.setSecurityId(src.getSecurityId());
        d.setBidPrice(src.getBidPrice());
        d.setAskPrice(src.getAskPrice());
        return d;
    }
}

