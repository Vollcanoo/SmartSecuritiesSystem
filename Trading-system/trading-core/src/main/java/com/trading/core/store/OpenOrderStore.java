package com.trading.core.store;

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

    public void add(OpenOrderRecord record) {
        if (record == null || record.getClOrderId() == null) return;
        byClOrderId.put(record.getClOrderId(), record);
    }

    public void remove(String clOrderId) {
        if (clOrderId != null) byClOrderId.remove(clOrderId);
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
}
