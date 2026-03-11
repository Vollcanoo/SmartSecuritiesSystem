package com.trading.core.service;

import com.trading.core.client.OrderEventSender;
import com.trading.core.engine.ExchangeEngineHolder;
import com.trading.core.model.OpenOrderRecord;
import com.trading.core.store.OpenOrderStore;
import exchange.core2.core.common.MatcherEventType;
import exchange.core2.core.common.MatcherTradeEvent;
import exchange.core2.core.common.cmd.OrderCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.trading.core.risk.SelfTradeChecker;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 后台线程消费引擎的 resultQueue，处理成交事件，更新挂单状态。
 * 当订单成交时，更新 OpenOrderStore 中的 filledQty，完全成交时移除挂单。
 */
@Service
public class TradeEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(TradeEventProcessor.class);

    private final ExchangeEngineHolder engineHolder;
    private final OpenOrderStore openOrderStore;
    private final OrderEventSender orderEventSender;
    private final SelfTradeChecker selfTradeChecker;
    /** 引擎订单ID -> 客户订单ID 的反向映射 */
    private final ConcurrentHashMap<Long, String> orderIdToClOrderId = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread processorThread;

    public TradeEventProcessor(ExchangeEngineHolder engineHolder,
                               OpenOrderStore openOrderStore,
                               OrderEventSender orderEventSender,
                               SelfTradeChecker selfTradeChecker) {
        this.engineHolder = engineHolder;
        this.openOrderStore = openOrderStore;
        this.orderEventSender = orderEventSender;
        this.selfTradeChecker = selfTradeChecker;
    }

    /**
     * 注册订单ID映射（下单时调用）
     */
    public void registerOrderMapping(Long engineOrderId, String clOrderId) {
        if (engineOrderId != null && clOrderId != null) {
            orderIdToClOrderId.put(engineOrderId, clOrderId);
            log.debug("Registered order mapping: engineOrderId={} -> clOrderId={}", engineOrderId, clOrderId);
        }
    }

    /**
     * 移除订单ID映射（撤单或完全成交时调用）
     */
    public void unregisterOrderMapping(Long engineOrderId) {
        if (engineOrderId != null) {
            orderIdToClOrderId.remove(engineOrderId);
        }
    }

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            processorThread = new Thread(this::processEvents, "TradeEventProcessor");
            processorThread.setDaemon(true);
            processorThread.start();
            log.info("TradeEventProcessor started");
        }
    }

    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (processorThread != null) {
                processorThread.interrupt();
                try {
                    processorThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("TradeEventProcessor stopped");
        }
    }

    private void processEvents() {
        BlockingQueue<OrderCommand> resultQueue = engineHolder.getResultQueue();
        log.info("TradeEventProcessor thread started, waiting for events from resultQueue...");
        while (running.get()) {
            OrderCommand cmd = null;
            try {
                cmd = resultQueue.take(); // 阻塞等待
                log.debug("Received OrderCommand from queue: orderId={}, command={}", cmd.orderId, cmd.command);
                processOrderCommand(cmd);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("TradeEventProcessor thread interrupted");
                break;
            } catch (Exception e) {
                log.error("Error processing order command: orderId={}", cmd != null ? cmd.orderId : "unknown", e);
            }
        }
        log.info("TradeEventProcessor thread stopped");
    }

    private void processOrderCommand(OrderCommand cmd) {
        if (cmd == null) {
            return;
        }
        
        // 记录所有收到的命令，用于调试
        if (cmd.matcherEvent != null) {
            log.debug("Processing OrderCommand: orderId={}, command={}, hasMatcherEvent=true", cmd.orderId, cmd.command);
        }

        if (cmd.matcherEvent == null) {
            // 没有成交事件，可能是撤单或其他命令，忽略
            return;
        }

        // 查找对应的客户订单ID
        String clOrderId = orderIdToClOrderId.get(cmd.orderId);
        log.debug("OrderCommand orderId={} -> clOrderId={}", cmd.orderId, clOrderId);

        // 遍历成交事件，计算总成交数量，并处理对手订单（maker）的成交
        long totalFilled = 0;
        boolean orderCompleted = false;
        MatcherTradeEvent event = cmd.matcherEvent;
        int tradeEventCount = 0;

        while (event != null) {
            if (event.eventType == MatcherEventType.TRADE) {
                tradeEventCount++;
                log.info("Trade event: orderId={}, matchedOrderId={}, size={}, activeCompleted={}, matchedCompleted={}", 
                    cmd.orderId, event.matchedOrderId, event.size, event.activeOrderCompleted, event.matchedOrderCompleted);
                
                // 处理对手订单（maker）的成交（无论当前订单是否在映射中）
                // 这是关键：当两个订单撮合时，maker 订单的成交事件会在 taker 订单的 OrderCommand 中
                if (event.matchedOrderId > 0) {
                    String makerClOrderId = orderIdToClOrderId.get(event.matchedOrderId);
                    
                    // 如果不在映射中，尝试从 OpenOrderStore 中查找（通过 engineOrderId）
                    if (makerClOrderId == null) {
                        makerClOrderId = findClOrderIdByEngineOrderId(event.matchedOrderId);
                    }
                    
                    if (makerClOrderId != null) {
                        log.info("Processing maker order fill: makerClOrderId={}, makerEngineOrderId={}, size={}, completed={}", 
                            makerClOrderId, event.matchedOrderId, event.size, event.matchedOrderCompleted);
                        updateOrderFillStatus(makerClOrderId, event.matchedOrderId, event.size, event.matchedOrderCompleted);
                    } else {
                        log.debug("Maker order not found: matchedOrderId={} (may be already filled or from previous session)", event.matchedOrderId);
                    }
                }
                
                // 只处理当前订单（taker）的成交，如果它在映射中
                // 注意：如果订单已经在 OrderService.placeOrder 中处理过（立即完全成交），
                // 这里 clOrderId 可能为 null（因为已经 unregister），这是正常的
                if (clOrderId != null) {
                    totalFilled += event.size;
                    if (event.activeOrderCompleted) {
                        orderCompleted = true;
                    }
                }
            }
            event = event.nextEvent;
        }

        log.debug("Processed {} trade events for orderId={}, totalFilled={}, orderCompleted={}, clOrderId={}", 
            tradeEventCount, cmd.orderId, totalFilled, orderCompleted, clOrderId);

        // 如果有成交，更新当前订单（taker）的挂单状态
        // 注意：如果订单已经在 OrderService.placeOrder 中完全成交并移除，clOrderId 可能为 null
        if (totalFilled > 0 && clOrderId != null) {
            log.info("Processing taker order fill: clOrderId={}, engineOrderId={}, totalFilled={}, orderCompleted={}", 
                clOrderId, cmd.orderId, totalFilled, orderCompleted);
            updateOrderFillStatus(clOrderId, cmd.orderId, totalFilled, orderCompleted);
        } else if (clOrderId == null && tradeEventCount > 0) {
            // 这种情况可能是订单已经在 OrderService 中处理过了（立即完全成交）
            // 但是，我们仍然需要处理 maker 订单的成交（已经在上面处理了）
            // 现在可以安全地取消注册映射了
            orderIdToClOrderId.remove(cmd.orderId);
            log.debug("Taker order already processed in OrderService, unregistering mapping: orderId={}, tradeEventCount={}", 
                cmd.orderId, tradeEventCount);
        }
    }

    /**
     * 更新订单的成交状态
     */
    private void updateOrderFillStatus(String clOrderId, Long engineOrderId, long filledQty, boolean orderCompleted) {
        OpenOrderRecord record = openOrderStore.get(clOrderId);
        if (record == null) {
            // 如果记录不存在，可能是订单已经在 OrderService 中处理过了（立即完全成交）
            // 但是，我们仍然需要发送成交事件来更新 Admin 模块的历史订单
            log.warn("OpenOrderRecord not found for clOrderId={}, engineOrderId={}, but processing fill event anyway", clOrderId, engineOrderId);
            
            // 尝试从映射中获取信息，发送成交事件
            // 注意：这种情况下，我们无法获取完整的订单信息，所以只能发送部分信息
            // 但是，Admin 模块应该已经有这个订单的记录（从 PLACED 事件中）
            sendFillEventWithoutRecord(clOrderId, engineOrderId, filledQty, orderCompleted);
            return;
        }

        int currentFilled = record.getFilledQty() != null ? record.getFilledQty() : 0;
        int newFilled = (int) (currentFilled + filledQty);
        int orderQty = record.getOrderQty() != null ? record.getOrderQty() : 0;

        record.setFilledQty(newFilled);

        // 通知风控模块扣减未成交敞口
        if (filledQty > 0) {
            selfTradeChecker.onTrade(clOrderId, filledQty);
        }

        // 如果完全成交，从挂单列表中移除
        if (orderCompleted || (orderQty > 0 && newFilled >= orderQty)) {
            openOrderStore.remove(clOrderId);
            orderIdToClOrderId.remove(engineOrderId);
            log.info("Order fully filled: clOrderId={}, engineOrderId={}, totalFilled={}", clOrderId, engineOrderId, newFilled);
            
            // 发送成交事件
            sendFillEvent(clOrderId, engineOrderId, record, newFilled, true);
        } else {
            log.debug("Order partially filled: clOrderId={}, engineOrderId={}, filled={}/{}", clOrderId, engineOrderId, newFilled, orderQty);
            
            // 发送部分成交事件
            sendFillEvent(clOrderId, engineOrderId, record, newFilled, false);
        }
    }

    /**
     * 发送成交事件（当记录不存在时使用，从 Admin 模块的历史记录中更新）
     */
    private void sendFillEventWithoutRecord(String clOrderId, Long engineOrderId, long filledQty, boolean fullyFilled) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", fullyFilled ? "FILLED" : "PARTIALLY_FILLED");
        event.put("clOrderId", clOrderId);
        event.put("engineOrderId", engineOrderId);
        event.put("filledQty", (int) filledQty);
        // 其他字段为 null，Admin 模块会从现有记录中获取
        orderEventSender.sendEvent(event);
        log.info("Sent fill event without record: clOrderId={}, engineOrderId={}, filledQty={}, fullyFilled={}", 
            clOrderId, engineOrderId, filledQty, fullyFilled);
    }

    /**
     * 通过 engineOrderId 查找 clOrderId
     * 首先从映射中查找，如果找不到，再从 OpenOrderStore 中查找
     */
    private String findClOrderIdByEngineOrderId(Long engineOrderId) {
        // 首先从映射中查找
        String clOrderId = orderIdToClOrderId.get(engineOrderId);
        if (clOrderId != null) {
            return clOrderId;
        }
        // 如果映射中没有，从 OpenOrderStore 中查找
        clOrderId = openOrderStore.findClOrderIdByEngineOrderId(engineOrderId);
        if (clOrderId != null) {
            return clOrderId;
        }
        // 如果都找不到，记录警告（订单可能已经完全成交并从所有地方移除了）
        log.warn("Cannot find clOrderId for engineOrderId={} in mapping or OpenOrderStore (order may be already fully filled)", engineOrderId);
        return null;
    }

    private void sendFillEvent(String clOrderId, Long engineOrderId, OpenOrderRecord record, int filledQty, boolean fullyFilled) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", fullyFilled ? "FILLED" : "PARTIALLY_FILLED");
        event.put("clOrderId", clOrderId);
        event.put("engineOrderId", engineOrderId);
        event.put("shareholderId", record.getShareholderId());
        event.put("market", record.getMarket());
        event.put("securityId", record.getSecurityId());
        event.put("side", record.getSide());
        event.put("price", record.getPrice());
        event.put("orderQty", record.getOrderQty());
        event.put("filledQty", filledQty);
        event.put("canceledQty", null);
        orderEventSender.sendEvent(event);

    }
}
