package com.trading.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 行情服务：通过新浪财经 HTTP 接口实时拉取 A 股/港股/美股行情。
 *
 * <p>数据源说明：
 * <ul>
 *   <li>A 股: hq.sinajs.cn （沪市 sh + 代码, 深市 sz + 代码）</li>
 *   <li>港股: hq.sinajs.cn/list=hk00700 等</li>
 *   <li>美股: hq.sinajs.cn/list=gb_aapl 等</li>
 * </ul>
 *
 * <p>用户通过 watchlist 管理要关注的证券代码，后端定时刷新行情快照。
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    /** 新浪行情 API 地址 */
    private static final String SINA_API = "https://hq.sinajs.cn/list=";

    /** 行情快照缓存：key = sinaSymbol (如 sh600519) */
    private final ConcurrentHashMap<String, MarketSnapshot> snapshots = new ConcurrentHashMap<>();

    /** 关注列表：存储 sina 格式代码 */
    private final Set<String> watchlist = ConcurrentHashMap.newKeySet();

    /** 预设热门 A 股 */
    private static final List<String> DEFAULT_A_SHARES = Arrays.asList(
            "sh600519", "sh601318", "sh600036", "sh601166", "sh600030",
            "sz000858", "sz000333", "sz002714", "sz300750", "sz000001"
    );

    /** 预设热门港股 */
    private static final List<String> DEFAULT_HK = Arrays.asList(
            "hk00700", "hk09988", "hk03690", "hk01810", "hk09999"
    );

    /** 预设热门美股 */
    private static final List<String> DEFAULT_US = Arrays.asList(
            "gb_aapl", "gb_googl", "gb_msft", "gb_tsla", "gb_nvda"
    );

    @PostConstruct
    public void init() {
        // 默认加入热门 A 股
        watchlist.addAll(DEFAULT_A_SHARES);
        log.info("MarketDataService initialized with {} default symbols", watchlist.size());
        // 启动时立即拉取一次
        refreshAll();
    }

    // ─────────────────────────────────────────────
    //  关注列表管理
    // ─────────────────────────────────────────────

    /**
     * 添加到关注列表。接受多种格式：
     * - 直接 sina 代码: sh600519, sz000001, hk00700, gb_aapl
     * - 简写: 600519 (自动判断 sh/sz), AAPL (自动加 gb_ 前缀)
     */
    public List<String> addToWatchlist(List<String> symbols) {
        List<String> added = new ArrayList<>();
        for (String raw : symbols) {
            String normalized = normalizeSinaSymbol(raw.trim());
            if (normalized != null && watchlist.add(normalized)) {
                added.add(normalized);
            }
        }
        if (!added.isEmpty()) {
            log.info("Added to watchlist: {}", added);
            // 立刻拉取新加入的
            fetchQuotes(added);
        }
        return added;
    }

    /**
     * 从关注列表移除。
     */
    public List<String> removeFromWatchlist(List<String> symbols) {
        List<String> removed = new ArrayList<>();
        for (String raw : symbols) {
            String normalized = normalizeSinaSymbol(raw.trim());
            if (normalized != null && watchlist.remove(normalized)) {
                snapshots.remove(normalized);
                removed.add(normalized);
            }
        }
        return removed;
    }

    /**
     * 获取当前关注列表。
     */
    public Set<String> getWatchlist() {
        return Collections.unmodifiableSet(watchlist);
    }

    /**
     * 添加预设板块。
     */
    public void addPreset(String preset) {
        switch (preset.toLowerCase()) {
            case "a-shares":
            case "a":
                watchlist.addAll(DEFAULT_A_SHARES);
                break;
            case "hk":
            case "hongkong":
                watchlist.addAll(DEFAULT_HK);
                break;
            case "us":
            case "usa":
                watchlist.addAll(DEFAULT_US);
                break;
            case "all":
                watchlist.addAll(DEFAULT_A_SHARES);
                watchlist.addAll(DEFAULT_HK);
                watchlist.addAll(DEFAULT_US);
                break;
        }
        refreshAll();
    }

    /**
     * 清空关注列表和行情缓存。
     */
    public void clearAll() {
        watchlist.clear();
        snapshots.clear();
        log.info("Watchlist and market data cleared");
    }

    // ─────────────────────────────────────────────
    //  定时刷新行情（每 5 秒）
    // ─────────────────────────────────────────────

    @Scheduled(fixedRate = 5000, initialDelay = 3000)
    public void refreshAll() {
        if (watchlist.isEmpty()) return;
        List<String> symbols = new ArrayList<>(watchlist);
        fetchQuotes(symbols);
    }

    /**
     * 从新浪 API 批量拉取行情。
     * 新浪支持一次请求多个代码，用逗号分隔。
     */
    private void fetchQuotes(List<String> symbols) {
        if (symbols.isEmpty()) return;

        // 分批请求，每批最多 30 个
        int batchSize = 30;
        for (int i = 0; i < symbols.size(); i += batchSize) {
            List<String> batch = symbols.subList(i, Math.min(i + batchSize, symbols.size()));
            String joined = String.join(",", batch);
            try {
                String response = httpGet(SINA_API + joined);
                if (response != null) {
                    parseSinaResponse(response);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch quotes for batch {}: {}", i / batchSize, e.getMessage());
            }
        }
    }

    /**
     * 解析新浪行情响应。
     *
     * A 股格式示例:
     * var hq_str_sh600519="贵州茅台,1811.00,1812.50,...";
     * 字段: 名称,今开,昨收,当前价,最高,最低,买一,卖一,成交量(股),成交额,...
     *
     * 港股格式: var hq_str_hk00700="TENCENT,...,现价,...";
     * 美股格式: var hq_str_gb_aapl="苹果,...,现价,...";
     */
    private void parseSinaResponse(String raw) {
        String[] lines = raw.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || !line.startsWith("var hq_str_")) continue;

            try {
                // 提取 symbol: var hq_str_sh600519="..."
                int eqIdx = line.indexOf('=');
                if (eqIdx < 0) continue;
                String symbolPart = line.substring(11, eqIdx); // skip "var hq_str_"
                String dataPart = line.substring(eqIdx + 1).replace("\"", "").replace(";", "").trim();

                if (dataPart.isEmpty()) {
                    // 无效代码或停牌
                    continue;
                }

                String[] fields = dataPart.split(",");

                MarketSnapshot snap = new MarketSnapshot();
                snap.setSinaSymbol(symbolPart);
                snap.setTimestamp(System.currentTimeMillis());

                if (symbolPart.startsWith("sh") || symbolPart.startsWith("sz")) {
                    parseAShareFields(snap, symbolPart, fields);
                } else if (symbolPart.startsWith("hk")) {
                    parseHKFields(snap, symbolPart, fields);
                } else if (symbolPart.startsWith("gb_")) {
                    parseUSFields(snap, symbolPart, fields);
                } else {
                    continue;
                }

                snapshots.put(symbolPart, snap);
            } catch (Exception e) {
                log.debug("Failed to parse line: {} — {}", line, e.getMessage());
            }
        }
    }

    /**
     * 解析 A 股字段。
     * 格式: 名称(0),今开(1),昨收(2),当前价(3),最高(4),最低(5),买一(6),卖一(7),成交量(8),成交额(9),...,日期(30),时间(31)
     */
    private void parseAShareFields(MarketSnapshot snap, String symbol, String[] f) {
        if (f.length < 10) return;
        snap.setName(f[0]);
        snap.setMarket(symbol.startsWith("sh") ? "SH" : "SZ");
        snap.setSecurityId(symbol.substring(2));
        snap.setOpenPrice(parseDoubleSafe(f[1]));
        snap.setPrevClose(parseDoubleSafe(f[2]));
        snap.setLastPrice(parseDoubleSafe(f[3]));
        snap.setHighPrice(parseDoubleSafe(f[4]));
        snap.setLowPrice(parseDoubleSafe(f[5]));
        snap.setBidPrice(parseDoubleSafe(f[6]));
        snap.setAskPrice(parseDoubleSafe(f[7]));
        snap.setVolume(parseLongSafe(f[8]));
        snap.setTurnover(parseDoubleSafe(f[9]));

        // 涨跌停: A 股普通股 ±10%
        if (snap.getPrevClose() > 0) {
            snap.setHighLimit(Math.round(snap.getPrevClose() * 1.10 * 100.0) / 100.0);
            snap.setLowLimit(Math.round(snap.getPrevClose() * 0.90 * 100.0) / 100.0);
        }

        // 如果当前价为 0（未开盘/停牌），使用昨收
        if (snap.getLastPrice() <= 0 && snap.getPrevClose() > 0) {
            snap.setLastPrice(snap.getPrevClose());
            snap.setStatus("HALT");
        } else {
            snap.setStatus("TRADING");
        }
    }

    /**
     * 解析港股字段。
     * 格式: 英文名(0),中文名(1),今开(2),昨收(3),最高(4),最低(5),现价(6),涨跌(7),涨幅(8),买入(9),卖出(10),成交量(11),成交额(12),...
     */
    private void parseHKFields(MarketSnapshot snap, String symbol, String[] f) {
        if (f.length < 13) return;
        snap.setName(f[1].isEmpty() ? f[0] : f[1]);
        snap.setMarket("HK");
        snap.setSecurityId(symbol.substring(2));
        snap.setOpenPrice(parseDoubleSafe(f[2]));
        snap.setPrevClose(parseDoubleSafe(f[3]));
        snap.setHighPrice(parseDoubleSafe(f[4]));
        snap.setLowPrice(parseDoubleSafe(f[5]));
        snap.setLastPrice(parseDoubleSafe(f[6]));
        snap.setBidPrice(parseDoubleSafe(f[9]));
        snap.setAskPrice(parseDoubleSafe(f[10]));
        snap.setVolume(parseLongSafe(f[11]));
        snap.setTurnover(parseDoubleSafe(f[12]));
        // 港股没有涨跌停
        snap.setHighLimit(0);
        snap.setLowLimit(0);
        snap.setStatus(snap.getLastPrice() > 0 ? "TRADING" : "CLOSED");
    }

    /**
     * 解析美股字段。
     * 格式: 名称(0),现价(1),涨跌(2),涨幅%(3),时间(4),...,成交量(10),...,昨收(26)
     */
    private void parseUSFields(MarketSnapshot snap, String symbol, String[] f) {
        if (f.length < 11) return;
        snap.setName(f[0]);
        snap.setMarket("US");
        snap.setSecurityId(symbol.substring(3).toUpperCase());
        snap.setLastPrice(parseDoubleSafe(f[1]));
        snap.setVolume(parseLongSafe(f[10]));
        // 美股昨收
        if (f.length > 26) {
            snap.setPrevClose(parseDoubleSafe(f[26]));
        } else {
            // 从涨跌额推算
            double change = parseDoubleSafe(f[2]);
            if (snap.getLastPrice() > 0 && change != 0) {
                snap.setPrevClose(snap.getLastPrice() - change);
            }
        }
        // 美股无涨跌停
        snap.setHighLimit(0);
        snap.setLowLimit(0);
        snap.setStatus(snap.getLastPrice() > 0 ? "TRADING" : "CLOSED");
    }

    // ─────────────────────────────────────────────
    //  查询接口
    // ─────────────────────────────────────────────

    public MarketSnapshot getSnapshot(String sinaSymbol) {
        return snapshots.get(sinaSymbol);
    }

    /**
     * 兼容旧接口：通过 market + securityId 查询。
     */
    public MarketSnapshot getSnapshot(String market, String securityId) {
        String sina = toSinaSymbol(market, securityId);
        return sina != null ? snapshots.get(sina) : null;
    }

    public Map<String, MarketSnapshot> getAllSnapshots() {
        return new ConcurrentHashMap<>(snapshots);
    }

    /**
     * 按市场筛选。
     */
    public Map<String, MarketSnapshot> getSnapshotsByMarket(String market) {
        return snapshots.entrySet().stream()
                .filter(e -> market.equalsIgnoreCase(e.getValue().getMarket()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 搜索（模糊匹配代码或名称）。
     */
    public List<MarketSnapshot> search(String keyword) {
        String kw = keyword.toLowerCase();
        return snapshots.values().stream()
                .filter(s -> s.getSecurityId().toLowerCase().contains(kw)
                        || (s.getName() != null && s.getName().contains(keyword)))
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    //  价格校验（供 OrderService 使用）
    // ─────────────────────────────────────────────

    /**
     * 价格校验结果。
     */
    public static class PriceCheckResult {
        private final boolean valid;
        private final String reason;

        public PriceCheckResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }
        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public static PriceCheckResult ok() { return new PriceCheckResult(true, null); }
        public static PriceCheckResult fail(String reason) { return new PriceCheckResult(false, reason); }
    }

    /**
     * 检查委托价格是否在行情允许范围内。
     */
    public PriceCheckResult checkPrice(String market, String securityId, String side, double price) {
        MarketSnapshot snapshot = getSnapshot(market, securityId);
        if (snapshot == null) {
            return PriceCheckResult.ok();
        }

        if (snapshot.getHighLimit() > 0 && price > snapshot.getHighLimit()) {
            return PriceCheckResult.fail(String.format("委托价格 %.2f 超过涨停价 %.2f", price, snapshot.getHighLimit()));
        }
        if (snapshot.getLowLimit() > 0 && price < snapshot.getLowLimit()) {
            return PriceCheckResult.fail(String.format("委托价格 %.2f 低于跌停价 %.2f", price, snapshot.getLowLimit()));
        }

        if ("B".equals(side) && snapshot.getAskPrice() > 0) {
            double maxBuyPrice = snapshot.getAskPrice() * 1.1;
            if (price > maxBuyPrice) {
                return PriceCheckResult.fail(String.format("买入价 %.2f 偏离卖一价 %.2f 超过10%%", price, snapshot.getAskPrice()));
            }
        }
        if ("S".equals(side) && snapshot.getBidPrice() > 0) {
            double minSellPrice = snapshot.getBidPrice() * 0.9;
            if (price < minSellPrice) {
                return PriceCheckResult.fail(String.format("卖出价 %.2f 偏离买一价 %.2f 超过10%%", price, snapshot.getBidPrice()));
            }
        }

        return PriceCheckResult.ok();
    }

    /**
     * 撮合参考价。
     */
    public double suggestMatchPrice(String market, String securityId, double buyPrice, double sellPrice) {
        MarketSnapshot snapshot = getSnapshot(market, securityId);
        if (snapshot == null || snapshot.getLastPrice() <= 0) {
            return Math.min(buyPrice, sellPrice) + (Math.max(buyPrice, sellPrice) - Math.min(buyPrice, sellPrice)) / 2.0;
        }
        double lastPrice = snapshot.getLastPrice();
        return Math.max(sellPrice, Math.min(buyPrice, lastPrice));
    }

    // ─────────────────────────────────────────────
    //  HTTP 工具
    // ─────────────────────────────────────────────

    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            // 新浪接口需要 Referer
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("HTTP {} from {}", code, urlStr);
                return null;
            }

            // 新浪返回 GBK 编码
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), Charset.forName("GBK")))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.debug("httpGet error: {}", e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────
    //  辅助方法
    // ─────────────────────────────────────────────

    /**
     * 将用户输入的各种格式归一化为 sina symbol。
     */
    public static String normalizeSinaSymbol(String input) {
        if (input == null || input.isEmpty()) return null;
        input = input.trim().toLowerCase();

        // 已经是标准格式
        if (input.matches("^(sh|sz)\\d{6}$")) return input;
        if (input.matches("^hk\\d{5}$")) return input;
        if (input.matches("^gb_[a-z]+$")) return input;

        // 纯 6 位数字：自动判断沪深
        if (input.matches("^\\d{6}$")) {
            // 6/9 开头上交所，0/3 开头深交所
            char first = input.charAt(0);
            if (first == '6' || first == '9') return "sh" + input;
            if (first == '0' || first == '3') return "sz" + input;
            if (first == '4' || first == '8') return "sz" + input; // 北交所在深交所代码体系
            return "sh" + input;
        }

        // 5 位数字：港股
        if (input.matches("^\\d{5}$")) return "hk" + input;

        // 纯字母：美股
        if (input.matches("^[a-z]+$")) return "gb_" + input;

        // 带点分隔: 600519.SH → sh600519
        if (input.matches("^\\d{6}\\.(sh|sz)$")) {
            String code = input.substring(0, 6);
            String ex = input.substring(7);
            return ex + code;
        }

        return null;
    }

    /**
     * market + securityId → sina symbol。
     */
    private String toSinaSymbol(String market, String securityId) {
        if (market == null || securityId == null) return null;
        switch (market.toUpperCase()) {
            case "SH": case "XSHG": return "sh" + securityId;
            case "SZ": case "XSHE": return "sz" + securityId;
            case "HK": return "hk" + securityId;
            case "US": return "gb_" + securityId.toLowerCase();
            default: return null;
        }
    }

    private double parseDoubleSafe(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0; }
    }

    private long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0; }
    }

    // ─────────────────────────────────────────────
    //  行情快照数据结构
    // ─────────────────────────────────────────────

    public static class MarketSnapshot {
        private String sinaSymbol;
        private String market;
        private String securityId;
        private String name;
        private double lastPrice;
        private double openPrice;
        private double highPrice;
        private double lowPrice;
        private double bidPrice;
        private double askPrice;
        private double highLimit;
        private double lowLimit;
        private double prevClose;
        private long volume;
        private double turnover;
        private long timestamp;
        private String status;  // TRADING, HALT, CLOSED

        // ── Getters & Setters ──
        public String getSinaSymbol() { return sinaSymbol; }
        public void setSinaSymbol(String sinaSymbol) { this.sinaSymbol = sinaSymbol; }
        public String getMarket() { return market; }
        public void setMarket(String market) { this.market = market; }
        public String getSecurityId() { return securityId; }
        public void setSecurityId(String securityId) { this.securityId = securityId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getLastPrice() { return lastPrice; }
        public void setLastPrice(double lastPrice) { this.lastPrice = lastPrice; }
        public double getOpenPrice() { return openPrice; }
        public void setOpenPrice(double openPrice) { this.openPrice = openPrice; }
        public double getHighPrice() { return highPrice; }
        public void setHighPrice(double highPrice) { this.highPrice = highPrice; }
        public double getLowPrice() { return lowPrice; }
        public void setLowPrice(double lowPrice) { this.lowPrice = lowPrice; }
        public double getBidPrice() { return bidPrice; }
        public void setBidPrice(double bidPrice) { this.bidPrice = bidPrice; }
        public double getAskPrice() { return askPrice; }
        public void setAskPrice(double askPrice) { this.askPrice = askPrice; }
        public double getHighLimit() { return highLimit; }
        public void setHighLimit(double highLimit) { this.highLimit = highLimit; }
        public double getLowLimit() { return lowLimit; }
        public void setLowLimit(double lowLimit) { this.lowLimit = lowLimit; }
        public double getPrevClose() { return prevClose; }
        public void setPrevClose(double prevClose) { this.prevClose = prevClose; }
        public long getVolume() { return volume; }
        public void setVolume(long volume) { this.volume = volume; }
        public double getTurnover() { return turnover; }
        public void setTurnover(double turnover) { this.turnover = turnover; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        @Override
        public String toString() {
            return String.format("MarketSnapshot{%s %s:%s %.2f bid=%.2f ask=%.2f vol=%d}",
                    name, market, securityId, lastPrice, bidPrice, askPrice, volume);
        }
    }
}
