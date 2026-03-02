package com.trading.admin.service;

import com.trading.admin.dto.OrderEventDto;
import com.trading.admin.entity.OrderHistory;
import com.trading.admin.repository.OrderHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderHistoryService {

    public static final String STATUS_LIVE = "LIVE";
    public static final String STATUS_PARTIALLY_FILLED = "PARTIALLY_FILLED";
    public static final String STATUS_FILLED = "FILLED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String EVENT_PLACED = "PLACED";
    public static final String EVENT_PARTIALLY_FILLED = "PARTIALLY_FILLED";
    public static final String EVENT_FILLED = "FILLED";
    public static final String EVENT_CANCELLED = "CANCELLED";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderHistoryService.class);

    private final OrderHistoryRepository repository;

    public OrderHistoryService(OrderHistoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 处理 Core 推送的订单事件，落库并更新状态。
     */
    @Transactional
    public void processEvent(OrderEventDto event) {
        if (event == null || event.getEventType() == null || event.getClOrderId() == null) return;
        String type = event.getEventType().toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        if (EVENT_PLACED.equals(type) || EVENT_PARTIALLY_FILLED.equals(type) || EVENT_FILLED.equals(type)) {
            Optional<OrderHistory> existing = repository.findByClOrderId(event.getClOrderId());
            int filled = event.getFilledQty() != null ? event.getFilledQty() : 0;
            
            if (existing.isPresent()) {
                OrderHistory o = existing.get();
                // 如果事件中没有提供 orderQty，使用现有记录中的值
                int orderQty = event.getOrderQty() != null ? event.getOrderQty() : (o.getOrderQty() != null ? o.getOrderQty() : 0);
                // 更新其他字段（如果事件中提供了）
                if (event.getShareholderId() != null) o.setShareholderId(event.getShareholderId());
                if (event.getMarket() != null) o.setMarket(event.getMarket());
                if (event.getSecurityId() != null) o.setSecurityId(event.getSecurityId());
                if (event.getSide() != null) o.setSide(event.getSide());
                if (event.getPrice() != null) o.setPrice(event.getPrice());
                if (event.getOrderQty() != null) o.setOrderQty(event.getOrderQty());
                
                o.setFilledQty(filled);
                // 计算状态：如果 filled >= orderQty，则为 FILLED；如果 filled > 0，则为 PARTIALLY_FILLED；否则为 LIVE
                String status = (orderQty > 0 && filled >= orderQty) ? STATUS_FILLED : (filled > 0 ? STATUS_PARTIALLY_FILLED : STATUS_LIVE);
                o.setStatus(status);
                o.setUpdatedAt(now);
                repository.save(o);
                log.debug("Updated order history: clOrderId={}, filled={}/{}, status={}", event.getClOrderId(), filled, orderQty, status);
            } else {
                // 新订单，创建记录
                int orderQty = event.getOrderQty() != null ? event.getOrderQty() : 0;
                String status = (orderQty > 0 && filled >= orderQty) ? STATUS_FILLED : (filled > 0 ? STATUS_PARTIALLY_FILLED : STATUS_LIVE);
                
                OrderHistory o = new OrderHistory();
                o.setClOrderId(event.getClOrderId());
                o.setEngineOrderId(event.getEngineOrderId());
                o.setShareholderId(event.getShareholderId());
                o.setMarket(event.getMarket());
                o.setSecurityId(event.getSecurityId());
                o.setSide(event.getSide());
                o.setPrice(event.getPrice());
                o.setOrderQty(orderQty);
                o.setFilledQty(filled);
                o.setCanceledQty(0);
                o.setStatus(status);
                o.setCreatedAt(now);
                o.setUpdatedAt(now);
                repository.save(o);
                log.debug("Created new order history: clOrderId={}, filled={}/{}, status={}", event.getClOrderId(), filled, orderQty, status);
            }
        } else if (EVENT_CANCELLED.equals(type)) {
            Optional<OrderHistory> existing = repository.findByClOrderId(event.getClOrderId());
            if (existing.isPresent()) {
                OrderHistory o = existing.get();
                o.setStatus(STATUS_CANCELLED);
                o.setCanceledQty(event.getCanceledQty() != null ? event.getCanceledQty() : 0);
                o.setUpdatedAt(now);
                repository.save(o);
            } else {
                log.info("Order event CANCELLED but no order in DB: clOrderId={}", event.getClOrderId());
            }
        }
    }

    public List<OrderHistory> findHistory(String shareholderId, String status, int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0) size = 50;
        if (size > 500) size = 500;
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sort);
        if (shareholderId == null || shareholderId.isBlank()) {
            return status != null && !status.isBlank()
                ? repository.findByStatusOrderByCreatedAtDesc(status, pageable)
                : repository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return status != null && !status.isBlank()
            ? repository.findByShareholderIdAndStatusOrderByCreatedAtDesc(shareholderId, status, pageable)
            : repository.findByShareholderIdOrderByCreatedAtDesc(shareholderId, pageable);
    }

    /**
     * 按 id 删除一条历史订单。
     */
    @Transactional
    public boolean deleteById(Long id) {
        if (id == null) return false;
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        log.info("Deleted order history id={}", id);
        return true;
    }

    /**
     * 删除全部历史订单。
     */
    @Transactional
    public long deleteAll() {
        long count = repository.count();
        repository.deleteAll();
        log.info("Deleted all order history, count={}", count);
        return count;
    }
}
