package com.trading.core.store;

import com.trading.common.model.request.MarketData;
import com.trading.core.market.MarketDataStore;
import com.trading.core.model.OpenOrderRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按股东号维护当前挂单（未撤、未完全成交的订单），供「查某用户挂单」使用。
 */
@Component
public class OpenOrderStore {

    /** clOrderId -> 订单快照 */
    private final ConcurrentHashMap<String, OpenOrderRecord> byClOrderId = new ConcurrentHashMap<>();

    private final MarketDataStore marketDataStore;

    public OpenOrderStore(MarketDataStore marketDataStore) {
        this.marketDataStore = marketDataStore;
    }

    public void add(OpenOrderRecord record) {
        if (record == null || record.getClOrderId() == null) return;
        byClOrderId.put(record.getClOrderId(), record);
        // 新挂单可能影响盘口，重新计算该股票的买一/卖一
        recalcTopOfBookFor(record.getMarket(), record.getSecurityId());
    }

    public void remove(String clOrderId) {
        if (clOrderId == null) {
            return;
        }
        OpenOrderRecord removed = byClOrderId.remove(clOrderId);
        if (removed != null) {
            recalcTopOfBookFor(removed.getMarket(), removed.getSecurityId());
        }
    }

    public OpenOrderRecord get(String clOrderId) {
        return clOrderId == null ? null : byClOrderId.get(clOrderId);
    }

    /**
     * 查询某股东号下的当前挂单（未撤单的）。
     */
    public List<OpenOrderRecord> listByShareholder(String shareholderId) {
        if (shareholderId == null) return new ArrayList<>();
        List<OpenOrderRecord> list = new ArrayList<>();
        for (OpenOrderRecord r : byClOrderId.values()) {
            if (shareholderId.equals(r.getShareholderId())) list.add(r);
        }
        return list;
    }

    /**
     * 通过 engineOrderId 查找 clOrderId
     */
    public String findClOrderIdByEngineOrderId(Long engineOrderId) {
        if (engineOrderId == null) return null;
        for (OpenOrderRecord record : byClOrderId.values()) {
            if (engineOrderId.equals(record.getEngineOrderId())) {
                return record.getClOrderId();
            }
        }
        return null;
    }

    /**
     * 重新计算某只股票的盘口行情（买一/卖一），并写入 MarketDataStore。
     * 规则：
     * - bidPrice = 所有 B 侧挂单中价格最高者
     * - askPrice = 所有 S 侧挂单中价格最低者
     * - 若某侧无挂单，则该侧为 null
     */
    public void recalcTopOfBookFor(String market, String securityId) {
        if (market == null || securityId == null) {
            return;
        }
        Double bestBid = null;
        Double bestAsk = null;

        for (OpenOrderRecord r : byClOrderId.values()) {
            if (!market.equals(r.getMarket()) || !securityId.equals(r.getSecurityId())) {
                continue;
            }
            if (r.getPrice() == null || r.getSide() == null) {
                continue;
            }
            int orderQty = r.getOrderQty() != null ? r.getOrderQty() : 0;
            int filledQty = r.getFilledQty() != null ? r.getFilledQty() : 0;
            int remaining = orderQty - filledQty;
            if (remaining <= 0) {
                continue;
            }
            if ("B".equals(r.getSide())) {
                if (bestBid == null || r.getPrice() > bestBid) {
                    bestBid = r.getPrice();
                }
            } else if ("S".equals(r.getSide())) {
                if (bestAsk == null || r.getPrice() < bestAsk) {
                    bestAsk = r.getPrice();
                }
            }
        }

        MarketData md = new MarketData();
        md.setMarket(market);
        md.setSecurityId(securityId);
        md.setBidPrice(bestBid);
        md.setAskPrice(bestAsk);
        marketDataStore.update(md);
    }
}
