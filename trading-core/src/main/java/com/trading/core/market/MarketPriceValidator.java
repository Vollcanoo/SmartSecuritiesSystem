package com.trading.core.market;

import com.trading.common.enums.RejectCode;
import com.trading.common.model.request.MarketData;
import com.trading.common.model.request.OrderRequest;
import com.trading.common.model.response.OrderRejectResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 行情价格校验：在订单进入撮合前，约束订单价格与当前行情的关系。
 */
@Component
public class MarketPriceValidator {

    public enum Mode {
        /** 严格等价：买价必须等于行情 ask，卖价等于行情 bid */
        EXACT,
        /** 区间模式：订单价格须在 [bid, ask] 区间内（若 bid/ask 同时存在） */
        INSIDE
    }

    private final MarketDataStore store;
    private final Mode mode;
    private final boolean requireMarket;

    public MarketPriceValidator(
            MarketDataStore store,
            @Value("${trading.core.market-validation.mode:EXACT}") String modeStr,
            @Value("${trading.core.market-validation.required:false}") boolean requireMarket
    ) {
        this.store = store;
        this.mode = Mode.valueOf(modeStr.toUpperCase());
        this.requireMarket = requireMarket;
    }

    /**
     * 返回 null 表示通过校验；返回 OrderRejectResponse 表示拒单。
     */
    public OrderRejectResponse validate(OrderRequest req) {
        if (req == null || req.getPrice() == null) {
            return null;
        }
        MarketData md = store.get(req.getMarket(), req.getSecurityId()).orElse(null);
        if (md == null) {
            if (requireMarket) {
                return reject(req, RejectCode.INVALID_PRICE.getCode(), "无行情，拒绝撮合");
            }
            return null;
        }

        double price = req.getPrice();
        Double bid = md.getBidPrice();
        Double ask = md.getAskPrice();
        boolean isBuy = "B".equals(req.getSide());

        switch (mode) {
            case EXACT:
                if (isBuy) {
                    if (ask == null || Double.compare(price, ask) != 0) {
                        return reject(req, RejectCode.INVALID_PRICE.getCode(),
                                "买入价格必须等于最新行情卖价(ask)");
                    }
                } else {
                    if (bid == null || Double.compare(price, bid) != 0) {
                        return reject(req, RejectCode.INVALID_PRICE.getCode(),
                                "卖出价格必须等于最新行情买价(bid)");
                    }
                }
                break;
            case INSIDE:
                if (bid != null && ask != null && bid <= ask) {
                    if (price < bid || price > ask) {
                        return reject(req, RejectCode.INVALID_PRICE.getCode(),
                                "订单价格须在最新行情买卖价区间内");
                    }
                }
                break;
            default:
                break;
        }
        return null;
    }

    private OrderRejectResponse reject(OrderRequest req, int code, String text) {
        OrderRejectResponse r = new OrderRejectResponse();
        if (req != null) {
            r.setClOrderId(req.getClOrderId());
            r.setMarket(req.getMarket());
            r.setSecurityId(req.getSecurityId());
            r.setSide(req.getSide());
            r.setQty(req.getQty());
            r.setPrice(req.getPrice());
            r.setShareholderId(req.getShareholderId());
        }
        r.setRejectCode(code);
        r.setRejectText(text);
        return r;
    }
}

