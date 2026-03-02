package com.trading.core.service;

import com.trading.common.model.request.CancelRequest;
import com.trading.common.model.request.OrderRequest;
import com.trading.common.model.response.*;
import com.trading.core.client.OrderEventSender;
import com.trading.core.engine.ExchangeEngineHolder;
import com.trading.core.model.OpenOrderRecord;
import com.trading.core.risk.CancelValidation;
import com.trading.core.risk.OrderValidation;
import com.trading.core.risk.SelfTradeChecker;
import com.trading.core.risk.UserValidator;
import com.trading.core.store.OpenOrderStore;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.OrderType;
import exchange.core2.core.common.cmd.CommandResultCode;
import exchange.core2.core.common.cmd.OrderCommand;
import exchange.core2.core.ExchangeApi;
import exchange.core2.core.common.api.ApiCancelOrder;
import exchange.core2.core.common.api.ApiPlaceOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单服务：将业务请求转换为引擎 API 调用，不修改引擎代码。
 * 当前固定 symbolId=1、uid=1，价格/数量按引擎精度换算。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int PRICE_SCALE = 10000;
    private static final long TIMEOUT_MS = 5000;

    private final ExchangeEngineHolder engineHolder;
    private final SelfTradeChecker selfTradeChecker;
    private final OpenOrderStore openOrderStore;
    private final OrderEventSender orderEventSender;
    private final UserValidator userValidator;
    private final TradeEventProcessor tradeEventProcessor;
    private final MarketDataService marketDataService;
    private final PerformanceMetricsService performanceMetrics;
    /** 客户委托编号 -> 引擎订单 ID，用于撤单时查找 */
    private final ConcurrentHashMap<String, Long> clOrderIdToOrderId = new ConcurrentHashMap<>();
    /** 订单号生成器：格式 ORD + 时间戳 + 4位随机数 */
    private final AtomicLong orderSequence = new AtomicLong(1L);

    public OrderService(ExchangeEngineHolder engineHolder, SelfTradeChecker selfTradeChecker, OpenOrderStore openOrderStore, OrderEventSender orderEventSender, UserValidator userValidator, TradeEventProcessor tradeEventProcessor, MarketDataService marketDataService, PerformanceMetricsService performanceMetrics) {
        this.engineHolder = engineHolder;
        this.selfTradeChecker = selfTradeChecker;
        this.openOrderStore = openOrderStore;
        this.orderEventSender = orderEventSender;
        this.userValidator = userValidator;
        this.tradeEventProcessor = tradeEventProcessor;
        this.marketDataService = marketDataService;
        this.performanceMetrics = performanceMetrics;
    }

    /**
     * 生成订单号：格式 ORD + 时间戳（秒）+ 4位序号
     */
    private String generateOrderId() {
        long timestamp = System.currentTimeMillis() / 1000;
        long seq = orderSequence.getAndIncrement() % 10000;
        return String.format("ORD%010d%04d", timestamp, seq);
    }

    /**
     * 生成撤单号：格式 CAN + 时间戳（秒）+ 4位序号
     */
    private String generateCancelId() {
        long timestamp = System.currentTimeMillis() / 1000;
        long seq = orderSequence.getAndIncrement() % 10000;
        return String.format("CAN%010d%04d", timestamp, seq);
    }

    /**
     * 下单：系统自动生成订单号，先做基本校验与对敲风控，再送引擎；返回 OrderAckResponse 或 OrderRejectResponse。
     */
    public Object placeOrder(OrderRequest req) {
        long startNanos = System.nanoTime();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        
        // 系统自动生成订单号（如果用户未提供）
        if (req.getClOrderId() == null || req.getClOrderId().trim().isEmpty()) {
            String generatedOrderId = generateOrderId();
            req.setClOrderId(generatedOrderId);
            log.debug("Auto-generated clOrderId: {}", generatedOrderId);
        }
        
        OrderRejectResponse validationReject = OrderValidation.validate(req);
        if (validationReject != null) {
            log.info("requestId={} clOrderId={} placeOrder validationReject code={}", requestId, req.getClOrderId(), validationReject.getRejectCode());
            performanceMetrics.recordReject();
            return validationReject;
        }
        OrderRejectResponse shareholderReject = userValidator.validateShareholder(req);
        if (shareholderReject != null) {
            log.info("requestId={} clOrderId={} placeOrder shareholderReject code={}", requestId, req != null ? req.getClOrderId() : null, shareholderReject.getRejectCode());
            performanceMetrics.recordReject();
            return shareholderReject;
        }
        
        // 行情价格校验（涨跌停 / 偏离度检查）
        if (req.getPrice() != null && req.getPrice() > 0) {
            MarketDataService.PriceCheckResult priceCheck = marketDataService.checkPrice(
                    req.getMarket(), req.getSecurityId(), req.getSide(), req.getPrice());
            if (!priceCheck.isValid()) {
                log.info("requestId={} clOrderId={} placeOrder priceCheck failed: {}", requestId, req.getClOrderId(), priceCheck.getReason());
                performanceMetrics.recordReject();
                return toOrderReject(req, 1006, priceCheck.getReason());
            }
        }
        
        OrderRejectResponse selfTradeReject = selfTradeChecker.checkAndRejectIfSelfTrade(req);
        if (selfTradeReject != null) {
            performanceMetrics.recordReject();
            return selfTradeReject;
        }

        ExchangeApi api = engineHolder.getApi();
        long orderId = engineHolder.nextOrderId();
        int symbolId = ExchangeEngineHolder.DEFAULT_SYMBOL_ID;
        long uid = ExchangeEngineHolder.DEFAULT_UID;

        long price = toEnginePrice(req.getPrice());
        long size = req.getQty() != null ? req.getQty() : 0;
        OrderAction action = "B".equals(req.getSide()) || "BUY".equalsIgnoreCase(req.getSide()) ? OrderAction.BID : OrderAction.ASK;
        // 引擎要求 BID 的 reserveBidPrice >= price，否则风控报错并统一返回 RISK_NSF
        long reservePrice = (action == OrderAction.BID) ? price : 0L;

        ApiPlaceOrder cmd = ApiPlaceOrder.builder()
                .orderId(orderId)
                .price(price)
                .size(size)
                .reservePrice(reservePrice)
                .action(action)
                .orderType(OrderType.GTC)
                .uid(uid)
                .symbol(symbolId)
                .userCookie(0)
                .build();

        try {
            CompletableFuture<OrderCommand> future = api.submitCommandAsyncFullResponse(cmd);
            OrderCommand result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (isSuccess(result.resultCode)) {
                clOrderIdToOrderId.put(req.getClOrderId(), orderId);
                // 注册订单映射，供 TradeEventProcessor 使用
                tradeEventProcessor.registerOrderMapping(orderId, req.getClOrderId());
                
                // 计算初始成交数量（从 matcherEvent 中计算）
                int filled = calculateFilledFromEvents(result);
                selfTradeChecker.onOrderAccepted(req, req.getClOrderId(), req.getQty() != null ? req.getQty() : 0, filled);
                OpenOrderRecord record = new OpenOrderRecord();
                record.setClOrderId(req.getClOrderId());
                record.setShareholderId(req.getShareholderId());
                record.setMarket(req.getMarket());
                record.setSecurityId(req.getSecurityId());
                record.setSide(req.getSide());
                record.setPrice(req.getPrice());
                record.setOrderQty(req.getQty());
                record.setFilledQty(filled);
                record.setEngineOrderId(orderId);
                openOrderStore.add(record);
                int orderQty = req.getQty() != null ? req.getQty() : 0;
                String eventType = (orderQty > 0 && filled >= orderQty) ? "FILLED" : "PLACED";
                // 注意：即使订单立即完全成交，也不要立即取消注册映射和移除挂单
                // 让 TradeEventProcessor 来处理，这样可以确保 maker 订单的成交也能被正确处理
                // 但是，如果订单立即完全成交，我们需要立即发送 FILLED 事件
                if ("FILLED".equals(eventType)) {
                    // 先发送 FILLED 事件，确保 Admin 模块能收到
                    sendOrderEvent(eventType, req.getClOrderId(), orderId, req.getShareholderId(), req.getMarket(), req.getSecurityId(), req.getSide(), req.getPrice(), orderQty, filled, null);
                    // 然后移除挂单，但保留映射，让 TradeEventProcessor 也能处理 maker 订单的成交
                    openOrderStore.remove(req.getClOrderId());
                    // 注意：不要立即取消注册映射，让 TradeEventProcessor 处理完后再取消
                    log.info("Order immediately fully filled: clOrderId={}, engineOrderId={}, filled={}, keeping mapping for TradeEventProcessor", 
                        req.getClOrderId(), orderId, filled);
                } else {
                    sendOrderEvent(eventType, req.getClOrderId(), orderId, req.getShareholderId(), req.getMarket(), req.getSecurityId(), req.getSide(), req.getPrice(), orderQty, filled, null);
                }
                log.info("requestId={} clOrderId={} placeOrder ack orderId={}", requestId, req.getClOrderId(), orderId);
                performanceMetrics.recordOrder(System.nanoTime() - startNanos);
                return toOrderAck(req, orderId, result);
            }
            log.info("requestId={} clOrderId={} placeOrder reject code={}", requestId, req.getClOrderId(), result.resultCode);
            performanceMetrics.recordReject();
            return toOrderReject(req, toRejectCode(result.resultCode), result.resultCode != null ? result.resultCode.name() : "REJECT");
        } catch (Exception e) {
            log.warn("requestId={} placeOrder failed: clOrderId={}", requestId, req.getClOrderId(), e);
            performanceMetrics.recordReject();
            return toOrderReject(req, 1001, e.getMessage());
        }
    }

    /**
     * 撤单：系统自动生成撤单号，只需提供原订单号（origClOrderId）。
     * 其他字段（shareholderId、market、securityId、side）从挂单记录自动获取。
     * 先校验请求，再根据 origClOrderId 查找引擎 orderId，同步等待引擎回报。
     */
    public Object cancelOrder(CancelRequest req) {
        long startNanos = System.nanoTime();
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        
        // 系统自动生成撤单号
        if (req.getClOrderId() == null || req.getClOrderId().trim().isEmpty()) {
            String generatedCancelId = generateCancelId();
            req.setClOrderId(generatedCancelId);
            log.debug("Auto-generated cancel clOrderId: {}", generatedCancelId);
        }
        
        // 从挂单记录获取原订单信息
        OpenOrderRecord openOrder = openOrderStore.get(req.getOrigClOrderId());
        if (openOrder == null) {
            return toCancelReject(req, 1004, "订单不存在或已撤单: " + req.getOrigClOrderId());
        }
        
        // 自动填充字段（如果用户未提供）
        if (req.getShareholderId() == null || req.getShareholderId().trim().isEmpty()) {
            req.setShareholderId(openOrder.getShareholderId());
        }
        if (req.getMarket() == null || req.getMarket().trim().isEmpty()) {
            req.setMarket(openOrder.getMarket());
        }
        if (req.getSecurityId() == null || req.getSecurityId().trim().isEmpty()) {
            req.setSecurityId(openOrder.getSecurityId());
        }
        if (req.getSide() == null || req.getSide().trim().isEmpty()) {
            req.setSide(openOrder.getSide());
        }
        
        CancelRejectResponse validationReject = CancelValidation.validate(req, openOrderStore);
        if (validationReject != null) {
            log.info("requestId={} origClOrderId={} cancelOrder validationReject code={}", requestId, req.getOrigClOrderId(), validationReject.getRejectCode());
            return validationReject;
        }
        Long engineOrderId = clOrderIdToOrderId.get(req.getOrigClOrderId());
        if (engineOrderId == null) {
            // 如果内存中没有，尝试从 openOrder 获取
            engineOrderId = openOrder.getEngineOrderId();
            if (engineOrderId == null) {
                return toCancelReject(req, 1004, "订单不存在: " + req.getOrigClOrderId());
            }
        }
        ExchangeApi api = engineHolder.getApi();
        int symbolId = ExchangeEngineHolder.DEFAULT_SYMBOL_ID;
        long uid = ExchangeEngineHolder.DEFAULT_UID;

        ApiCancelOrder cmd = ApiCancelOrder.builder()
                .orderId(engineOrderId)
                .uid(uid)
                .symbol(symbolId)
                .build();

        try {
            CompletableFuture<OrderCommand> future = api.submitCommandAsyncFullResponse(cmd);
            OrderCommand result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (isSuccess(result.resultCode)) {
                CancelAckResponse ack = toCancelAck(req, result);
                int canceledQty = ack.getCanceledQty() != null ? ack.getCanceledQty() : 0;
                selfTradeChecker.onCancelAccepted(req.getOrigClOrderId(), canceledQty);
                openOrderStore.remove(req.getOrigClOrderId());
                clOrderIdToOrderId.remove(req.getOrigClOrderId());
                // 取消注册订单映射
                tradeEventProcessor.unregisterOrderMapping(engineOrderId);
                sendOrderEvent("CANCELLED", req.getOrigClOrderId(), engineOrderId, req.getShareholderId(), req.getMarket(), req.getSecurityId(), req.getSide(), null, null, null, canceledQty);
                log.info("requestId={} origClOrderId={} cancelOrder ack canceledQty={}", requestId, req.getOrigClOrderId(), canceledQty);
                performanceMetrics.recordCancel();
                performanceMetrics.recordOrder(System.nanoTime() - startNanos);
                return ack;
            }
            log.info("requestId={} origClOrderId={} cancelOrder reject code={}", requestId, req.getOrigClOrderId(), result.resultCode);
            return toCancelReject(req, toRejectCode(result.resultCode), result.resultCode != null ? result.resultCode.name() : "REJECT");
        } catch (Exception e) {
            log.warn("requestId={} cancelOrder failed: orderId={}", requestId, engineOrderId, e);
            return toCancelReject(req, 1004, e.getMessage());
        }
    }

    /**
     * 查询某股东号下的当前挂单（未撤单的订单列表）。
     */
    public List<OpenOrderRecord> getOpenOrders(String shareholderId) {
        return openOrderStore.listByShareholder(shareholderId);
    }

    private void sendOrderEvent(String eventType, String clOrderId, Long engineOrderId, String shareholderId, String market, String securityId, String side, Double price, Integer orderQty, Integer filledQty, Integer canceledQty) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("clOrderId", clOrderId);
        event.put("engineOrderId", engineOrderId);
        event.put("shareholderId", shareholderId);
        event.put("market", market);
        event.put("securityId", securityId);
        event.put("side", side);
        event.put("price", price);
        event.put("orderQty", orderQty);
        event.put("filledQty", filledQty);
        event.put("canceledQty", canceledQty);
        orderEventSender.sendEvent(event);
    }

    private static boolean isSuccess(CommandResultCode code) {
        return code == CommandResultCode.SUCCESS || code == CommandResultCode.ACCEPTED || code == CommandResultCode.VALID_FOR_MATCHING_ENGINE || code == CommandResultCode.NEW;
    }

    private static int toRejectCode(CommandResultCode code) {
        if (code == null) return 1001;
        switch (code) {
            case INVALID_SYMBOL: return 1005;
            case MATCHING_UNKNOWN_ORDER_ID: return 1004;
            case RISK_NSF: return 1003;
            default: return 1001;
        }
    }

    private OrderAckResponse toOrderAck(OrderRequest req, long orderId, OrderCommand cmd) {
        OrderAckResponse ack = new OrderAckResponse();
        ack.setClOrderId(req.getClOrderId());
        ack.setMarket(req.getMarket());
        ack.setSecurityId(req.getSecurityId());
        ack.setSide(req.getSide());
        ack.setQty(req.getQty());
        ack.setPrice(req.getPrice());
        ack.setShareholderId(req.getShareholderId());
        ack.setOrderId(Long.valueOf(orderId));
        return ack;
    }

    private OrderRejectResponse toOrderReject(OrderRequest req, int code, String text) {
        OrderRejectResponse r = new OrderRejectResponse();
        r.setClOrderId(req.getClOrderId());
        r.setMarket(req.getMarket());
        r.setSecurityId(req.getSecurityId());
        r.setSide(req.getSide());
        r.setQty(req.getQty());
        r.setPrice(req.getPrice());
        r.setShareholderId(req.getShareholderId());
        r.setRejectCode(code);
        r.setRejectText(text != null ? text : "Unknown error");
        return r;
    }

    private CancelAckResponse toCancelAck(CancelRequest req, OrderCommand cmd) {
        CancelAckResponse ack = new CancelAckResponse();
        ack.setClOrderId(req.getClOrderId());
        ack.setOrigClOrderId(req.getOrigClOrderId());
        ack.setMarket(req.getMarket());
        ack.setSecurityId(req.getSecurityId());
        ack.setShareholderId(req.getShareholderId());
        ack.setSide(req.getSide());
        ack.setQty((int) cmd.size);
        ack.setPrice(fromEnginePrice(cmd.price));
        ack.setCumQty((int) cmd.getFilled());
        ack.setCanceledQty((int) (cmd.size - cmd.getFilled()));
        return ack;
    }

    private CancelRejectResponse toCancelReject(CancelRequest req, int code, String text) {
        CancelRejectResponse r = new CancelRejectResponse();
        r.setClOrderId(req.getClOrderId());
        r.setOrigClOrderId(req.getOrigClOrderId());
        r.setRejectCode(code);
        r.setRejectText(text != null ? text : "Unknown error");
        return r;
    }

    private static long toEnginePrice(Double price) {
        if (price == null) return 0;
        return (long) (price * PRICE_SCALE);
    }

    private static double fromEnginePrice(long price) {
        return price / (double) PRICE_SCALE;
    }

    /**
     * 从 OrderCommand 的 matcherEvent 中计算成交数量
     */
    private int calculateFilledFromEvents(OrderCommand cmd) {
        if (cmd == null || cmd.matcherEvent == null) {
            return 0;
        }
        long totalFilled = 0;
        exchange.core2.core.common.MatcherTradeEvent event = cmd.matcherEvent;
        while (event != null) {
            if (event.eventType == exchange.core2.core.common.MatcherEventType.TRADE) {
                totalFilled += event.size;
            }
            event = event.nextEvent;
        }
        return (int) totalFilled;
    }
}
