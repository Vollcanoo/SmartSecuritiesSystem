package com.trading.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 性能指标服务：统计订单处理的吞吐量和延迟。
 * <p>
 * - 实时统计每秒处理订单数（OPS）
 * - 记录延迟分布（p50/p90/p99/max）
 * - 支持基准测试模式（批量压测）
 */
@Service
public class PerformanceMetricsService {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMetricsService.class);

    /** 延迟样本数组（微秒），固定大小环形缓冲区 */
    private static final int SAMPLE_SIZE = 100_000;
    private final long[] latencySamples = new long[SAMPLE_SIZE];
    private final AtomicLong sampleIndex = new AtomicLong(0);

    /** 计数器 */
    private final LongAdder totalOrders = new LongAdder();
    private final LongAdder totalTrades = new LongAdder();
    private final LongAdder totalCancels = new LongAdder();
    private final LongAdder totalRejects = new LongAdder();

    /** 每秒统计窗口 */
    private final ConcurrentHashMap<Long, LongAdder> opsPerSecond = new ConcurrentHashMap<>();
    private volatile long startTimeMs = System.currentTimeMillis();

    /**
     * 记录订单处理延迟（微秒）。
     */
    public void recordLatency(long latencyMicros) {
        int idx = (int) (sampleIndex.getAndIncrement() % SAMPLE_SIZE);
        latencySamples[idx] = latencyMicros;
    }

    /**
     * 记录一笔订单处理。
     */
    public void recordOrder(long latencyNanos) {
        totalOrders.increment();
        recordLatency(latencyNanos / 1000); // 转为微秒
        long sec = System.currentTimeMillis() / 1000;
        opsPerSecond.computeIfAbsent(sec, k -> new LongAdder()).increment();
    }

    /**
     * 记录一笔成交。
     */
    public void recordTrade() {
        totalTrades.increment();
    }

    /**
     * 记录一笔撤单。
     */
    public void recordCancel() {
        totalCancels.increment();
    }

    /**
     * 记录一笔拒绝。
     */
    public void recordReject() {
        totalRejects.increment();
    }

    /**
     * 获取性能统计快照。
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();

        // 基本计数
        metrics.put("totalOrders", totalOrders.sum());
        metrics.put("totalTrades", totalTrades.sum());
        metrics.put("totalCancels", totalCancels.sum());
        metrics.put("totalRejects", totalRejects.sum());

        // 运行时间
        long elapsed = System.currentTimeMillis() - startTimeMs;
        metrics.put("uptimeMs", elapsed);
        metrics.put("uptimeSeconds", elapsed / 1000);

        // 平均吞吐量
        double avgOps = elapsed > 0 ? totalOrders.sum() * 1000.0 / elapsed : 0;
        metrics.put("avgOrdersPerSecond", Math.round(avgOps * 100) / 100.0);

        // 最近1秒的OPS
        long currentSec = System.currentTimeMillis() / 1000;
        LongAdder lastSecAdder = opsPerSecond.get(currentSec - 1);
        metrics.put("lastSecondOps", lastSecAdder != null ? lastSecAdder.sum() : 0);

        // 延迟统计
        metrics.put("latency", calculateLatencyStats());

        // 清理过期的OPS数据（保留最近60秒）
        cleanOpsHistory(currentSec);

        return metrics;
    }

    /**
     * 计算延迟统计（p50/p90/p99/max）。
     */
    private Map<String, Object> calculateLatencyStats() {
        long count = sampleIndex.get();
        int sampleCount = (int) Math.min(count, SAMPLE_SIZE);

        Map<String, Object> stats = new LinkedHashMap<>();
        if (sampleCount == 0) {
            stats.put("sampleCount", 0);
            stats.put("p50_us", 0);
            stats.put("p90_us", 0);
            stats.put("p99_us", 0);
            stats.put("max_us", 0);
            stats.put("avg_us", 0);
            return stats;
        }

        long[] sorted = new long[sampleCount];
        System.arraycopy(latencySamples, 0, sorted, 0, sampleCount);
        Arrays.sort(sorted);

        long sum = 0;
        for (long v : sorted) sum += v;

        stats.put("sampleCount", sampleCount);
        stats.put("p50_us", sorted[sampleCount / 2]);
        stats.put("p90_us", sorted[(int) (sampleCount * 0.9)]);
        stats.put("p99_us", sorted[(int) (sampleCount * 0.99)]);
        stats.put("max_us", sorted[sampleCount - 1]);
        stats.put("avg_us", sum / sampleCount);

        return stats;
    }

    /**
     * 重置所有统计数据。
     */
    public void reset() {
        totalOrders.reset();
        totalTrades.reset();
        totalCancels.reset();
        totalRejects.reset();
        sampleIndex.set(0);
        opsPerSecond.clear();
        startTimeMs = System.currentTimeMillis();
        Arrays.fill(latencySamples, 0);
        log.info("Performance metrics reset");
    }

    /**
     * 执行基准测试：批量发送模拟订单，返回吞吐量和延迟。
     */
    public Map<String, Object> runBenchmark(int orderCount, OrderService orderService) {
        log.info("Starting benchmark: {} orders", orderCount);
        reset();

        long benchStart = System.nanoTime();
        int success = 0, reject = 0;

        for (int i = 0; i < orderCount; i++) {
            com.trading.common.model.request.OrderRequest req = new com.trading.common.model.request.OrderRequest();
            req.setMarket("XSHG");
            req.setSecurityId("999999");
            req.setSide(i % 2 == 0 ? "B" : "S");
            req.setQty(100);
            req.setPrice(10.0 + (i % 100) * 0.01);
            req.setShareholderId("BENCH" + String.format("%05d", i % 10));

            long t0 = System.nanoTime();
            Object result = orderService.placeOrder(req);
            long latency = System.nanoTime() - t0;
            recordOrder(latency);

            if (result instanceof com.trading.common.model.response.OrderAckResponse) {
                success++;
            } else {
                reject++;
                recordReject();
            }
        }

        long benchEnd = System.nanoTime();
        double totalMs = (benchEnd - benchStart) / 1_000_000.0;
        double opsPerSec = orderCount / (totalMs / 1000.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderCount", orderCount);
        result.put("successCount", success);
        result.put("rejectCount", reject);
        result.put("totalTimeMs", Math.round(totalMs * 100) / 100.0);
        result.put("throughput_ops", Math.round(opsPerSec));
        result.put("latency", calculateLatencyStats());

        log.info("Benchmark complete: {} orders in {}ms = {} ops/s", orderCount, Math.round(totalMs), Math.round(opsPerSec));
        return result;
    }

    private void cleanOpsHistory(long currentSec) {
        opsPerSecond.entrySet().removeIf(e -> e.getKey() < currentSec - 60);
    }
}
