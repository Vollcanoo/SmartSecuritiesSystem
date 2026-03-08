package com.trading.admin.service;

import com.trading.admin.dto.OrderEventDto;
import com.trading.admin.entity.OrderHistory;
import com.trading.admin.repository.OrderHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

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
    
    // Batch processing queue
    private final BlockingQueue<OrderEventDto> eventQueue = new LinkedBlockingQueue<>(50000);
    private final ExecutorService batchExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean isRunning = true;

    public OrderHistoryService(OrderHistoryRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        batchExecutor.submit(this::processBatchLoop);
    }

    @PreDestroy
    public void shutdown() {
        isRunning = false;
        batchExecutor.shutdown();
        try {
            if (!batchExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                batchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 将订单事件放入队列，异步批量处理
     */
    public void processEvent(OrderEventDto event) {
        if (event == null || event.getEventType() == null || event.getClOrderId() == null) return;
        if (!eventQueue.offer(event)) {
            log.warn("Event queue is full, dropping event for clOrderId={}", event.getClOrderId());
        }
    }

    private void processBatchLoop() {
        while (isRunning || !eventQueue.isEmpty()) {
            try {
                List<OrderEventDto> batch = new ArrayList<>();
                OrderEventDto first = eventQueue.poll(1, TimeUnit.SECONDS);
                if (first != null) {
                    batch.add(first);
                    eventQueue.drainTo(batch, 999); // max 1000 per batch
                    saveBatch(batch);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing event batch", e);
            }
        }
    }

    @Transactional
    protected void saveBatch(List<OrderEventDto> batch) {
        if (batch.isEmpty()) return;
        
        // 分组处理同一个 clOrderId 的多个事件，只保留最终状态。这样可以避免同批次内重复查询和插入冲突
        Map<String, OrderEventDto> mergedEvents = new LinkedHashMap<>();
        for (OrderEventDto e : batch) {
            mergedEvents.put(e.getClOrderId(), e); // 后面到达的覆盖前面的，这里做了简化，假设只有状态和数量更新
        }
        
        List<String> clOrderIds = new ArrayList<>(mergedEvents.keySet());
        List<OrderHistory> existingRecordsList = repository.findByClOrderIdIn(clOrderIds);

        Map<String, OrderHistory> existingMap = new HashMap<>();
        for (OrderHistory o : existingRecordsList) {
            existingMap.put(o.getClOrderId(), o);
        }

        List<OrderHistory> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (OrderEventDto event : mergedEvents.values()) {
            String type = event.getEventType().toUpperCase();
            
            if (EVENT_PLACED.equals(type) || EVENT_PARTIALLY_FILLED.equals(type) || EVENT_FILLED.equals(type)) {
                int filled = event.getFilledQty() != null ? event.getFilledQty() : 0;
                OrderHistory o = existingMap.get(event.getClOrderId());
                
                if (o != null) {
                    int orderQty = event.getOrderQty() != null ? event.getOrderQty() : (o.getOrderQty() != null ? o.getOrderQty() : 0);
                    if (event.getShareholderId() != null) o.setShareholderId(event.getShareholderId());
                    if (event.getMarket() != null) o.setMarket(event.getMarket());
                    if (event.getSecurityId() != null) o.setSecurityId(event.getSecurityId());
                    if (event.getSide() != null) o.setSide(event.getSide());
                    if (event.getPrice() != null) o.setPrice(event.getPrice());
                    if (event.getOrderQty() != null) o.setOrderQty(event.getOrderQty());
                    
                    o.setFilledQty(filled);
                    String status = (orderQty > 0 && filled >= orderQty) ? STATUS_FILLED : (filled > 0 ? STATUS_PARTIALLY_FILLED : STATUS_LIVE);
                    o.setStatus(status);
                    o.setUpdatedAt(now);
                    toSave.add(o);
                } else {
                    int orderQty = event.getOrderQty() != null ? event.getOrderQty() : 0;
                    String status = (orderQty > 0 && filled >= orderQty) ? STATUS_FILLED : (filled > 0 ? STATUS_PARTIALLY_FILLED : STATUS_LIVE);
                    
                    o = new OrderHistory();
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
                    toSave.add(o);
                }
            } else if (EVENT_CANCELLED.equals(type)) {
                OrderHistory o = existingMap.get(event.getClOrderId());
                if (o != null) {
                    o.setStatus(STATUS_CANCELLED);
                    o.setCanceledQty(event.getCanceledQty() != null ? event.getCanceledQty() : 0);
                    o.setUpdatedAt(now);
                    toSave.add(o);
                }
            }
        }
        
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
            log.debug("Batch saved {} order history records", toSave.size());
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

    @Transactional
    public boolean deleteById(Long id) {
        if (id == null) return false;
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        log.info("Deleted order history id={}", id);
        return true;
    }

    @Transactional
    public long deleteAll() {
        long count = repository.count();
        repository.deleteAll();
        log.info("Deleted all order history, count={}", count);
        return count;
    }
}
