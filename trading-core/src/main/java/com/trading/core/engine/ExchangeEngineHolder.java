package com.trading.core.engine;

import exchange.core2.core.ExchangeApi;
import exchange.core2.core.ExchangeCore;
import exchange.core2.core.common.api.ApiAddUser;
import exchange.core2.core.common.api.ApiAdjustUserBalance;
import exchange.core2.core.common.api.binary.BatchAddSymbolsCommand;
import exchange.core2.core.common.config.ExchangeConfiguration;
import exchange.core2.core.common.config.InitialStateConfiguration;
import exchange.core2.core.common.config.OrdersProcessingConfiguration;
import exchange.core2.core.common.config.PerformanceConfiguration;
import exchange.core2.core.common.cmd.CommandResultCode;
import exchange.core2.core.common.cmd.OrderCommand;
import exchange.core2.core.common.CoreSymbolSpecification;
import exchange.core2.core.common.SymbolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 持有 exchange-core 引擎实例，不修改引擎代码，仅在本类中调用其 API。
 * 通过 resultsConsumer 接收引擎回写，放入队列供业务层消费。
 */
@Component
public class ExchangeEngineHolder {

    private static final Logger log = LoggerFactory.getLogger(ExchangeEngineHolder.class);

    /** 默认品种 ID，与 initSymbol 一致 */
    public static final int DEFAULT_SYMBOL_ID = 1;
    /** 默认用户 ID，与 initUser 一致 */
    public static final long DEFAULT_UID = 1L;

    private ExchangeCore exchangeCore;
    private ExchangeApi api;

    /** 引擎回写命令放入此队列，由 OrderResultHandler 消费并转为业务回报 */
    private final BlockingQueue<OrderCommand> resultQueue = new LinkedBlockingQueue<>(64 * 1024);

    /** 全局递增订单 ID（引擎侧 orderId） */
    private final AtomicLong orderIdGenerator = new AtomicLong(1L);

    /** 动态 symbolId 生成器（从 2 开始，1 保留给默认） */
    private final AtomicInteger symbolIdGenerator = new AtomicInteger(1);

    /** 证券代码 -> 引擎 symbolId 的映射，key = "market:securityId" */
    private final ConcurrentHashMap<String, Integer> symbolRegistry = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        ExchangeConfiguration cfg = buildConfig();
        exchangeCore = ExchangeCore.builder()
                .exchangeConfiguration(cfg)
                .resultsConsumer(this::onResult)
                .build();
        exchangeCore.startup();
        api = exchangeCore.getApi();
        // 等待引擎 disruptor 线程就绪后再发二进制命令，避免首次提交超时
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before initSymbol", e);
        }
        initSymbol();
        initUser();
        initBalance();
        log.info("ExchangeCore started with symbolId=1, uid=1, default user balance initialized.");
    }

    @PreDestroy
    public void shutdown() {
        if (exchangeCore != null) {
            exchangeCore.shutdown(5, TimeUnit.SECONDS);
            log.info("ExchangeCore shutdown.");
        }
    }

    private ExchangeConfiguration buildConfig() {
        return ExchangeConfiguration.defaultBuilder()
                .ordersProcessingCfg(OrdersProcessingConfiguration.DEFAULT)
                .performanceCfg(PerformanceConfiguration.baseBuilder().build())
                .initStateCfg(InitialStateConfiguration.cleanStart("trading-core"))
                .build();
    }

    private void initSymbol() {
        CoreSymbolSpecification spec = CoreSymbolSpecification.builder()
                .symbolId(DEFAULT_SYMBOL_ID)
                .type(SymbolType.CURRENCY_EXCHANGE_PAIR)
                .baseCurrency(1)
                .quoteCurrency(2)
                .baseScaleK(1L)
                .quoteScaleK(10000L)
                .takerFee(0L)
                .makerFee(0L)
                .marginBuy(0L)
                .marginSell(0L)
                .build();
        try {
            CommandResultCode code = api.submitBinaryDataAsync(new BatchAddSymbolsCommand(spec)).get(30, TimeUnit.SECONDS);
            if (code != CommandResultCode.SUCCESS && code != CommandResultCode.ACCEPTED) {
                log.warn("BatchAddSymbols result: {}", code);
            }
        } catch (Exception e) {
            log.error("Init symbol failed", e);
            throw new IllegalStateException("Init symbol failed", e);
        }
    }

    private void initUser() {
        try {
            OrderCommand result = api.submitCommandAsyncFullResponse(ApiAddUser.builder().uid(DEFAULT_UID).build()).get(10, TimeUnit.SECONDS);
            if (result.resultCode != CommandResultCode.SUCCESS && result.resultCode != CommandResultCode.USER_MGMT_USER_ALREADY_EXISTS) {
                log.warn("Create user result: {}", result.resultCode);
            }
        } catch (Exception e) {
            log.error("Create user failed", e);
            throw new IllegalStateException("Create user failed", e);
        }
    }

    /** 为默认用户注入测试资金（报价货币=2）与持仓（基础货币=1），便于买/卖单都能通过引擎风控。引擎内所有请求共用 uid=1。 */
    private void initBalance() {
        final int quoteCurrency = 2;
        final int baseCurrency = 1;
        final long initialQuote = 1_000_000_000_000_000L;  // 资金（大额），确保测试期间不会触发 RISK_NSF
        final long initialBase = 1_000_000_000L;            // 持仓（大额），确保测试期间不会触发 RISK_NSF
        ApiAdjustUserBalance cmdQuote = ApiAdjustUserBalance.builder()
                .uid(DEFAULT_UID)
                .currency(quoteCurrency)
                .amount(initialQuote)
                .transactionId(1L)
                .build();
        ApiAdjustUserBalance cmdBase = ApiAdjustUserBalance.builder()
                .uid(DEFAULT_UID)
                .currency(baseCurrency)
                .amount(initialBase)
                .transactionId(2L)
                .build();
        try {
            OrderCommand r1 = api.submitCommandAsyncFullResponse(cmdQuote).get(10, TimeUnit.SECONDS);
            if (r1.resultCode != CommandResultCode.SUCCESS && r1.resultCode != CommandResultCode.ACCEPTED) {
                log.warn("Init balance quote result: {}", r1.resultCode);
            }
            OrderCommand r2 = api.submitCommandAsyncFullResponse(cmdBase).get(10, TimeUnit.SECONDS);
            if (r2.resultCode != CommandResultCode.SUCCESS && r2.resultCode != CommandResultCode.ACCEPTED) {
                log.warn("Init balance base result: {}", r2.resultCode);
            }
        } catch (Exception e) {
            log.error("Init balance failed", e);
            throw new IllegalStateException("Init balance failed", e);
        }
    }

    /**
     * 获取或动态注册证券对应的 symbolId。
     * 不同的 market:securityId 组合会分配不同的 symbolId，
     * 确保不同证券的订单簿相互隔离。
     */
    public int getOrCreateSymbolId(String market, String securityId) {
        String key = market + ":" + securityId;
        return symbolRegistry.computeIfAbsent(key, k -> {
            int newSymbolId = symbolIdGenerator.incrementAndGet();
            registerSymbolInEngine(newSymbolId);
            log.info("Registered new symbol: {} -> symbolId={}", key, newSymbolId);
            return newSymbolId;
        });
    }

    /**
     * 在引擎中注册一个新的 symbolId（与默认 symbol 使用相同参数）。
     */
    private void registerSymbolInEngine(int symbolId) {
        CoreSymbolSpecification spec = CoreSymbolSpecification.builder()
                .symbolId(symbolId)
                .type(SymbolType.CURRENCY_EXCHANGE_PAIR)
                .baseCurrency(1)
                .quoteCurrency(2)
                .baseScaleK(1L)
                .quoteScaleK(10000L)
                .takerFee(0L)
                .makerFee(0L)
                .marginBuy(0L)
                .marginSell(0L)
                .build();
        try {
            CommandResultCode code = api.submitBinaryDataAsync(new BatchAddSymbolsCommand(spec))
                    .get(10, TimeUnit.SECONDS);
            if (code != CommandResultCode.SUCCESS && code != CommandResultCode.ACCEPTED) {
                log.warn("Register symbol {} result: {}", symbolId, code);
            }
        } catch (Exception e) {
            log.error("Register symbol {} failed", symbolId, e);
            throw new IllegalStateException("Register symbol failed: " + symbolId, e);
        }
    }

    private void onResult(OrderCommand cmd, long sequence) {
        if (!resultQueue.offer(cmd.copy())) {
            log.warn("Result queue full, drop result for orderId={}", cmd.orderId);
        }
    }

    public ExchangeApi getApi() {
        return api;
    }

    public BlockingQueue<OrderCommand> getResultQueue() {
        return resultQueue;
    }

    public long nextOrderId() {
        return orderIdGenerator.incrementAndGet();
    }
}
