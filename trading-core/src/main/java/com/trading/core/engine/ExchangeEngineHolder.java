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

    /** securityId（股票代码，如 600030）到 symbolId（引擎内部品种ID）的映射 */
    private final ConcurrentHashMap<String, Integer> securityIdToSymbolId = new ConcurrentHashMap<>();
    /** symbolId 计数器，从 1 开始递增 */
    private final AtomicLong symbolIdCounter = new AtomicLong(1L);
    /** 已初始化的 symbolId 集合 */
    private final ConcurrentHashMap<Integer, Boolean> initializedSymbols = new ConcurrentHashMap<>();
    /** 已初始化的用户 uid 集合 */
    private final ConcurrentHashMap<Long, Boolean> initializedUsers = new ConcurrentHashMap<>();

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
        final long initialQuote = 1_000_000_000_000L;   // 资金，足够多笔买单
        final long initialBase = 10_000_000L;           // 持仓（引擎单位 = 股×baseScaleK），足够多笔卖单
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

    /** 为指定用户注入测试资金（报价货币=2）与持仓（基础货币=1） */
    public void initUserAndBalance(long uid) {
        try {
            OrderCommand result = api.submitCommandAsyncFullResponse(ApiAddUser.builder().uid(uid).build()).get(10, TimeUnit.SECONDS);
            if (result.resultCode != CommandResultCode.SUCCESS && result.resultCode != CommandResultCode.USER_MGMT_USER_ALREADY_EXISTS) {
                log.warn("Create user {} result: {}", uid, result.resultCode);
            }
        } catch (Exception e) {
            log.error("Create user {} failed", uid, e);
            throw new IllegalStateException("Create user failed", e);
        }

        final int quoteCurrency = 2;
        final int baseCurrency = 1;
        final long initialQuote = 1_000_000_000_000L;   // 资金，足够多笔买单
        final long initialBase = 10_000_000L;           // 持仓（引擎单位 = 股×baseScaleK），足够多笔卖单
        
        // 使用时间戳作为事务ID保证唯一
        long txQuote = System.nanoTime();
        long txBase = txQuote + 1;

        ApiAdjustUserBalance cmdQuote = ApiAdjustUserBalance.builder()
                .uid(uid)
                .currency(quoteCurrency)
                .amount(initialQuote)
                .transactionId(txQuote)
                .build();
        ApiAdjustUserBalance cmdBase = ApiAdjustUserBalance.builder()
                .uid(uid)
                .currency(baseCurrency)
                .amount(initialBase)
                .transactionId(txBase)
                .build();
        try {
            OrderCommand r1 = api.submitCommandAsyncFullResponse(cmdQuote).get(10, TimeUnit.SECONDS);
            if (r1.resultCode != CommandResultCode.SUCCESS && r1.resultCode != CommandResultCode.ACCEPTED) {
                log.warn("Init balance quote result for uid {}: {}", uid, r1.resultCode);
            }
            OrderCommand r2 = api.submitCommandAsyncFullResponse(cmdBase).get(10, TimeUnit.SECONDS);
            if (r2.resultCode != CommandResultCode.SUCCESS && r2.resultCode != CommandResultCode.ACCEPTED) {
                log.warn("Init balance base result for uid {}: {}", uid, r2.resultCode);
            }
        } catch (Exception e) {
            log.error("Init balance failed for uid {}", uid, e);
            throw new IllegalStateException("Init balance failed", e);
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

    /**
     * 根据 shareholderId 解析出 uid，如果该用户未在引擎中初始化，则自动初始化并分配测试资金。
     * @param shareholderId 如 "SH00010001"
     * @return 解析后的 uid
     */
    public long getUidAndInit(String shareholderId) {
        long uid;
        try {
            uid = Long.parseLong(shareholderId.toUpperCase().replace("SH", ""));
        } catch (Exception e) {
            log.warn("Invalid shareholderId format: {}, fallback to DEFAULT_UID", shareholderId);
            return DEFAULT_UID;
        }

        // lazy initialize
        if (initializedUsers.putIfAbsent(uid, true) == null) {
            log.info("Lazy initializing engine user and balance for uid={}", uid);
            initUserAndBalance(uid);
        }
        return uid;
    }

    /**
     * 根据 securityId（股票代码）获取对应的 symbolId。
     * 如果该股票尚未初始化，则自动创建新的引擎品种，并等待初始化完成。
     * 这样每只股票都有独立的订单簿，避免跨股票撮合。
     *
     * @param securityId 股票代码，如 "600030"
     * @return 对应的引擎 symbolId
     */
    public int getSymbolId(String securityId) {
        // 先检查映射表中是否已有该股票对应的 symbolId
        Integer symbolId = securityIdToSymbolId.get(securityId);
        if (symbolId != null) {
            // 检查是否已初始化完成
            if (initializedSymbols.containsKey(symbolId)) {
                return symbolId;
            }
            // 如果正在初始化中，等待一下
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return symbolId;
        }

        // 映射表中没有，则分配新的 symbolId 并初始化品种
        int newSymbolId = (int) symbolIdCounter.incrementAndGet();
        securityIdToSymbolId.put(securityId, newSymbolId);
        
        // 同步等待品种初始化完成
        initializeSymbolAndWait(newSymbolId);

        log.info("Registered new security {} -> symbolId {}", securityId, newSymbolId);
        return newSymbolId;
    }

    /**
     * 初始化新的交易品种（Symbol），并等待初始化完成
     * 注意：所有品种共用同一个资金池（baseCurrency=1, quoteCurrency=2），
     * 通过不同的 symbolId 来隔离订单簿，避免跨股票撮合。
     */
    private void initializeSymbolAndWait(int symbolId) {
        if (initializedSymbols.containsKey(symbolId)) {
            return;
        }

        // 所有品种共用 baseCurrency=1 和 quoteCurrency=2
        // symbolId 只用于隔离不同的订单簿
        CoreSymbolSpecification spec = CoreSymbolSpecification.builder()
                .symbolId(symbolId)
                .type(SymbolType.CURRENCY_EXCHANGE_PAIR)
                .baseCurrency(1)       // 统一使用 currency=1 作为基础货币
                .quoteCurrency(2)     // 统一使用 currency=2 作为报价货币
                .baseScaleK(1L)
                .quoteScaleK(10000L)
                .takerFee(0L)
                .makerFee(0L)
                .marginBuy(0L)
                .marginSell(0L)
                .build();

        try {
            // 等待异步初始化完成
            CommandResultCode code = api.submitBinaryDataAsync(new BatchAddSymbolsCommand(spec)).get(30, TimeUnit.SECONDS);
            if (code != CommandResultCode.SUCCESS && code != CommandResultCode.ACCEPTED) {
                log.warn("Initialize symbol {} result: {}", symbolId, code);
            } else {
                initializedSymbols.put(symbolId, true);
                log.info("Successfully initialized symbolId={} for trading", symbolId);
            }
        } catch (Exception e) {
            log.error("Initialize symbol {} failed", symbolId, e);
            throw new IllegalStateException("Initialize symbol failed", e);
        }
    }
}
