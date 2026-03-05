package com.trading.core.risk;

import com.trading.common.model.request.CancelRequest;
import com.trading.common.model.response.CancelRejectResponse;
import com.trading.core.model.OpenOrderRecord;
import com.trading.core.store.OpenOrderStore;

/**
 * 撤单请求基本校验：origClOrderId 非空与长度；可选校验与挂单股东号/方向一致。
 */
public final class CancelValidation {

    private static final int MAX_CL_ORDER_ID_LEN = 64;

    /**
     * 校验撤单请求；无效时返回非法回报，有效时返回 null。
     */
    public static CancelRejectResponse validate(CancelRequest req, OpenOrderStore openOrderStore) {
        if (req == null) {
            return reject(req, 1001, "撤单请求为空");
        }
        // origClOrderId 必须提供（要撤的订单号）
        String orig = req.getOrigClOrderId();
        if (orig == null || orig.trim().isEmpty()) {
            return reject(req, 1001, "origClOrderId 不能为空");
        }
        if (orig.length() > MAX_CL_ORDER_ID_LEN) {
            return reject(req, 1001, "origClOrderId 超过长度限制");
        }
        // clOrderId（撤单号）现在由系统自动生成，如果用户提供了则校验长度，否则跳过
        if (req.getClOrderId() != null && !req.getClOrderId().trim().isEmpty() && req.getClOrderId().length() > MAX_CL_ORDER_ID_LEN) {
            return reject(req, 1001, "clOrderId 超过长度限制");
        }
        OpenOrderRecord open = openOrderStore.get(orig);
        if (open != null && req.getShareholderId() != null && !req.getShareholderId().trim().isEmpty()) {
            if (!req.getShareholderId().equals(open.getShareholderId())) {
                return reject(req, 1001, "撤单股东号与挂单不一致");
            }
        }
        return null;
    }

    private static CancelRejectResponse reject(CancelRequest req, int code, String text) {
        CancelRejectResponse r = new CancelRejectResponse();
        if (req != null) {
            r.setClOrderId(req.getClOrderId());
            r.setOrigClOrderId(req.getOrigClOrderId());
        }
        r.setRejectCode(code);
        r.setRejectText(text);
        return r;
    }
}
