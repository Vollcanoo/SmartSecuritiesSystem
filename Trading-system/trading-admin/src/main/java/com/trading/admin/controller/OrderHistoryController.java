package com.trading.admin.controller;

import com.trading.admin.entity.OrderHistory;
import com.trading.admin.service.OrderHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单历史查询 API（按股东号、状态查数据库中的历史订单）。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderHistoryController {

    private final OrderHistoryService orderHistoryService;

    public OrderHistoryController(OrderHistoryService orderHistoryService) {
        this.orderHistoryService = orderHistoryService;
    }

    /**
     * 查询历史订单（分页、按创建时间倒序）。
     * @param shareholderId 股东号，可选
     * @param status 状态：LIVE/PARTIALLY_FILLED/FILLED/CANCELLED，可选
     * @param page 页码，从 0 开始，默认 0
     * @param size 每页条数，默认 50，最大 500
     */
    @GetMapping("/history")
    public ResponseEntity<List<OrderHistory>> history(
            @RequestParam(required = false) String shareholderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        List<OrderHistory> list = orderHistoryService.findHistory(shareholderId, status, page, size);
        return ResponseEntity.ok(list);
    }

    /**
     * 删除单条历史订单。
     * @param id 订单主键 id
     */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteById(@PathVariable Long id) {
        boolean deleted = orderHistoryService.deleteById(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除全部历史订单。
     */
    @DeleteMapping("/history")
    public ResponseEntity<DeleteAllResponse> deleteAll() {
        long count = orderHistoryService.deleteAll();
        return ResponseEntity.ok(new DeleteAllResponse(count));
    }

    /** 删除全部返回体 */
    public static class DeleteAllResponse {
        private long deletedCount;
        public DeleteAllResponse(long deletedCount) { this.deletedCount = deletedCount; }
        public long getDeletedCount() { return deletedCount; }
    }
}
