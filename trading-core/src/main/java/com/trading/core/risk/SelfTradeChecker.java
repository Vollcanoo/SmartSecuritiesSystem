package com.trading.core.risk;

import com.trading.common.enums.RejectCode;
import com.trading.common.model.request.OrderRequest;
import com.trading.common.model.response.OrderRejectResponse;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 需求文档 2.1.2：对敲风控。
 * 同一股东号下，同市场同证券不能同时存在未成交的买与卖（否则视为对敲），
 * 会触发对敲的订单不进入撮合并输出交易非法回报（rejectCode=1002）。
 */
@Component
public final class SelfTradeChecker {

    private static final String SEP = "|";

    private final ConcurrentHashMap<String, OpenQty> keyToOpen = new ConcurrentHashMap<>();
    /** 原委托 clOrderId -> (shareholderId, market, securityId, side)，撤单成功时用于扣减敞口 */
    private final ConcurrentHashMap<String, OrderKey> clOrderIdToKey = new ConcurrentHashMap<>();

    private static class OpenQty {
        final AtomicLong buyQty = new AtomicLong(0);
        final AtomicLong sellQty = new AtomicLong(0);
    }

    private static class OrderKey {
        final String shareholderId;
        final String market;
        final String securityId;
        final boolean isBuy;

        OrderKey(String shareholderId, String market, String securityId, boolean isBuy) {
            this.shareholderId = shareholderId;
            this.market = market;
            this.securityId = securityId;
            this.isBuy = isBuy;
        }
    }

    private static String key(String shareholderId, String market, String securityId) {
        return shareholderId + SEP + market + SEP + securityId;
    }

    /**
     * 检查是否会触发对敲：若同股东号同市场同证券下已有反向未成交挂单则返回非法回报，否则返回 null。
     */
    public OrderRejectResponse checkAndRejectIfSelfTrade(OrderRequest req) {
        String k = key(req.getShareholderId(), req.getMarket(), req.getSecurityId());
        OpenQty open = keyToOpen.get(k);
        if (open == null) return null;
        boolean isBuy = "B".equals(req.getSide());
        long opposite = isBuy ? open.sellQty.get() : open.buyQty.get();
        if (opposite > 0) {
            OrderRejectResponse r = new OrderRejectResponse();
            r.setClOrderId(req.getClOrderId());
            r.setMarket(req.getMarket());
            r.setSecurityId(req.getSecurityId());
            r.setSide(req.getSide());
            r.setQty(req.getQty());
            r.setPrice(req.getPrice());
            r.setShareholderId(req.getShareholderId());
            r.setRejectCode(RejectCode.SELF_MATCH.getCode());
            r.setRejectText(RejectCode.SELF_MATCH.getDescription() + "，同股东号下存在反向未成交订单");
            return r;
        }
        return null;
    }

    /**
     * 下单被交易所确认后调用：增加该侧未成交量（订单量 - 已成交量）。
     */
    public void onOrderAccepted(OrderRequest req, String clOrderId, int orderQty, int filledQty) {
        int leave = orderQty - filledQty;
        if (leave <= 0) return;
        String k = key(req.getShareholderId(), req.getMarket(), req.getSecurityId());
        boolean isBuy = "B".equals(req.getSide());
        keyToOpen.computeIfAbsent(k, x -> new OpenQty());
        OpenQty open = keyToOpen.get(k);
        if (isBuy) open.buyQty.addAndGet(leave); else open.sellQty.addAndGet(leave);
        clOrderIdToKey.put(clOrderId, new OrderKey(req.getShareholderId(), req.getMarket(), req.getSecurityId(), isBuy));
    }

    /**
     * 撤单被确认后调用：扣减该订单的未成交量。
     */
    public void onCancelAccepted(String origClOrderId, int canceledQty) {
        if (canceledQty <= 0) return;
        OrderKey ok = clOrderIdToKey.remove(origClOrderId);
        if (ok == null) return;
        String k = key(ok.shareholderId, ok.market, ok.securityId);
        OpenQty open = keyToOpen.get(k);
        if (open == null) return;
        if (ok.isBuy) open.buyQty.addAndGet(-canceledQty); else open.sellQty.addAndGet(-canceledQty);
    }

    /**
     * 引擎发生部分或全部成交时调用：扣减该订单对应的未成交量敞口。
     * @param clOrderId 客户订单ID
     * @param filledQty 本次新增的成交量
     */
    public void onTrade(String clOrderId, long filledQty) {
        if (filledQty <= 0 || clOrderId == null) return;
        OrderKey ok = clOrderIdToKey.get(clOrderId);
        if (ok == null) return;
        String k = key(ok.shareholderId, ok.market, ok.securityId);
        OpenQty open = keyToOpen.get(k);
        if (open == null) return;
        if (ok.isBuy) open.buyQty.addAndGet(-filledQty); else open.sellQty.addAndGet(-filledQty);
    }
}
