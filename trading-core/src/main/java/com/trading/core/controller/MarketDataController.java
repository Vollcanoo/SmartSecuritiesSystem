package com.trading.core.controller;

import com.trading.core.service.MarketDataService;
import com.trading.core.service.MarketDataService.MarketSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 行情数据 REST API。
 *
 * <p>接口概览：
 * <ul>
 *   <li>GET  /api/market-data               — 获取所有已关注的行情快照</li>
 *   <li>GET  /api/market-data?market=SH      — 按市场过滤</li>
 *   <li>GET  /api/market-data/search?q=茅台  — 搜索（代码/名称）</li>
 *   <li>GET  /api/market-data/watchlist       — 查看关注列表</li>
 *   <li>POST /api/market-data/watch           — 添加关注 {"symbols":["600519","AAPL"]}</li>
 *   <li>POST /api/market-data/unwatch         — 取消关注 {"symbols":["sh600519"]}</li>
 *   <li>POST /api/market-data/preset/{name}   — 添加预设板块 (a/hk/us/all)</li>
 *   <li>POST /api/market-data/refresh         — 手动触发刷新</li>
 *   <li>DELETE /api/market-data               — 清空全部</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/market-data")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * 获取所有行情快照（可选按市场过滤）。
     */
    @GetMapping
    public ResponseEntity<?> getAllSnapshots(@RequestParam(required = false) String market) {
        Map<String, MarketSnapshot> data;
        if (market != null && !market.isEmpty()) {
            data = marketDataService.getSnapshotsByMarket(market);
        } else {
            data = marketDataService.getAllSnapshots();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("count", data.size());
        result.put("data", data);
        return ResponseEntity.ok(result);
    }

    /**
     * 搜索行情（模糊匹配代码或名称）。
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        List<MarketSnapshot> results = marketDataService.search(q);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("count", results.size());
        result.put("data", results);
        return ResponseEntity.ok(result);
    }

    /**
     * 查看当前关注列表。
     */
    @GetMapping("/watchlist")
    public ResponseEntity<?> getWatchlist() {
        Set<String> wl = marketDataService.getWatchlist();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("count", wl.size());
        result.put("data", wl);
        return ResponseEntity.ok(result);
    }

    /**
     * 添加关注（支持各种格式：600519, AAPL, hk00700 等）。
     */
    @PostMapping("/watch")
    public ResponseEntity<?> addWatch(@RequestBody Map<String, List<String>> body) {
        List<String> symbols = body.getOrDefault("symbols", Collections.emptyList());
        if (symbols.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "symbols 不能为空"));
        }
        List<String> added = marketDataService.addToWatchlist(symbols);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "已添加 " + added.size() + " 个证券");
        result.put("added", added);
        result.put("watchlistSize", marketDataService.getWatchlist().size());
        return ResponseEntity.ok(result);
    }

    /**
     * 取消关注。
     */
    @PostMapping("/unwatch")
    public ResponseEntity<?> removeWatch(@RequestBody Map<String, List<String>> body) {
        List<String> symbols = body.getOrDefault("symbols", Collections.emptyList());
        List<String> removed = marketDataService.removeFromWatchlist(symbols);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "已移除 " + removed.size() + " 个证券");
        result.put("removed", removed);
        return ResponseEntity.ok(result);
    }

    /**
     * 添加预设板块 (a / hk / us / all)。
     */
    @PostMapping("/preset/{name}")
    public ResponseEntity<?> addPreset(@PathVariable String name) {
        marketDataService.addPreset(name);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "已加载预设板块: " + name);
        result.put("watchlistSize", marketDataService.getWatchlist().size());
        return ResponseEntity.ok(result);
    }

    /**
     * 手动触发刷新。
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh() {
        marketDataService.refreshAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "行情已刷新");
        result.put("count", marketDataService.getAllSnapshots().size());
        return ResponseEntity.ok(result);
    }

    /**
     * 清空全部关注和行情数据。
     */
    @DeleteMapping
    public ResponseEntity<?> clearAll() {
        marketDataService.clearAll();
        return ResponseEntity.ok(Map.of("code", 0, "message", "已清空所有行情数据和关注列表"));
    }
}
