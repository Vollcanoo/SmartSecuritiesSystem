package com.trading.core.risk;

import com.trading.common.enums.RejectCode;
import com.trading.common.model.request.OrderRequest;
import com.trading.common.model.response.OrderRejectResponse;

/**
 * 需求文档 2.1.1：基本交易校验，无效订单输出交易非法回报。
 * 约定：market 仅接受 XSHG/XSHE/BJSE，side 为 B/S。
 */
public final class OrderValidation {

    private static final String[] VALID_MARKETS = {"XSHG", "XSHE", "BJSE"};

    /**
     * 校验订单，无效时返回非法回报，有效时返回 null。
     */
    public static OrderRejectResponse validate(OrderRequest req) {
        if (req == null) {
            return reject(req, RejectCode.INVALID_ORDER, "订单为空");
        }
        // clOrderId 现在由系统自动生成，如果用户提供了则校验长度，否则跳过
        if (req.getClOrderId() != null && !req.getClOrderId().trim().isEmpty() && req.getClOrderId().length() > 64) {
            return reject(req, RejectCode.INVALID_ORDER, "clOrderId 超过64字符");
        }
        if (isEmpty(req.getShareholderId()) || req.getShareholderId().length() > 10) {
            return reject(req, RejectCode.INVALID_SHAREHOLDER_ID, "股东号无效或超过10字符");
        }
        if (!isValidMarket(req.getMarket())) {
            return reject(req, RejectCode.INVALID_MARKET, "市场代码须为 XSHG/XSHE/BJSE");
        }
        if (isEmpty(req.getSecurityId()) || req.getSecurityId().length() > 6) {
            return reject(req, RejectCode.INVALID_SYMBOL, "证券代码无效或超过6字符");
        }
        String side = req.getSide();
        if (side == null || (!"B".equals(side) && !"S".equals(side))) {
            return reject(req, RejectCode.INVALID_ORDER, "买卖方向须为 B 或 S");
        }
        if (req.getQty() == null || req.getQty() <= 0) {
            return reject(req, RejectCode.INVALID_QTY, "数量须大于0");
        }
        if (req.getPrice() == null || req.getPrice() <= 0) {
            return reject(req, RejectCode.INVALID_PRICE, "价格须大于0");
        }
        return null;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static boolean isValidMarket(String market) {
        if (market == null) return false;
        for (String m : VALID_MARKETS) {
            if (m.equals(market)) return true;
        }
        return false;
    }

    private static OrderRejectResponse reject(OrderRequest req, RejectCode code, String text) {
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
        r.setRejectCode(code.getCode());
        r.setRejectText(text);
        return r;
    }
}
