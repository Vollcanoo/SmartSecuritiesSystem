package com.trading.admin.service;

import com.trading.admin.repository.OrderHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class StartupDataAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StartupDataAnalysisService.class);
    private static final String REPORT_FILE_NAME = "startup-analysis.md";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("#,##0.00");

    private final OrderHistoryRepository orderHistoryRepository;

    public StartupDataAnalysisService(OrderHistoryRepository orderHistoryRepository) {
        this.orderHistoryRepository = orderHistoryRepository;
    }

    /**
     * Generate a snapshot report on each admin startup.
     * Failure should not block service startup.
     */
    public void generateStartupReport() {
        try {
            long totalOrders = orderHistoryRepository.countAllOrders();
            long totalFilledQty = orderHistoryRepository.sumFilledQty();
            BigDecimal totalTradedAmount = safeAmount(orderHistoryRepository.sumTradedAmount());
            List<OrderHistoryRepository.StockHotStat> hotStats = orderHistoryRepository.findTopStocksByFilledQty();
            List<OrderHistoryRepository.MarketVolumeStat> marketStats = orderHistoryRepository.findMarketVolumeStats();

            Path reportPath = resolveReportPath();
            String content = buildMarkdownReport(totalOrders, totalFilledQty, totalTradedAmount, hotStats, marketStats);
            writeReport(reportPath, content);
            log.info("Startup analysis report generated: {}", reportPath.toAbsolutePath());
        } catch (Exception e) {
            log.warn("Startup analysis report generation failed: {}", e.getMessage(), e);
            try {
                Path reportPath = resolveReportPath();
                writeReport(reportPath, buildFailureReport(e));
                log.info("Startup analysis fallback report generated: {}", reportPath.toAbsolutePath());
            } catch (Exception ex) {
                log.warn("Startup analysis fallback report generation failed: {}", ex.getMessage(), ex);
            }
        }
    }

    private String buildMarkdownReport(
        long totalOrders,
        long totalFilledQty,
        BigDecimal totalTradedAmount,
        List<OrderHistoryRepository.StockHotStat> hotStats,
        List<OrderHistoryRepository.MarketVolumeStat> marketStats
    ) {
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# Trading System Startup Analysis").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append("- Generated At: ").append(now).append(System.lineSeparator());
        sb.append("- Scope: full history in `t_order_history`").append(System.lineSeparator());
        sb.append("- Hot stock metric: total filled quantity (`filled_qty`)").append(System.lineSeparator()).append(System.lineSeparator());

        sb.append("## Overview").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append("| Metric | Value |").append(System.lineSeparator());
        sb.append("| --- | ---: |").append(System.lineSeparator());
        sb.append("| Total Orders | ").append(totalOrders).append(" |").append(System.lineSeparator());
        sb.append("| Total Filled Quantity | ").append(totalFilledQty).append(" |").append(System.lineSeparator());
        sb.append("| Total Traded Amount | ").append(formatAmount(totalTradedAmount)).append(" |").append(System.lineSeparator()).append(System.lineSeparator());

        sb.append("## Top Hot Stocks (By Filled Quantity)").append(System.lineSeparator()).append(System.lineSeparator());
        if (hotStats == null || hotStats.isEmpty()) {
            sb.append("No order data yet.").append(System.lineSeparator()).append(System.lineSeparator());
        } else {
            sb.append("| Rank | Market | Security | Filled Qty | Order Count | Traded Amount |").append(System.lineSeparator());
            sb.append("| ---: | --- | --- | ---: | ---: | ---: |").append(System.lineSeparator());
            int rank = 1;
            for (OrderHistoryRepository.StockHotStat stat : hotStats) {
                sb.append("| ").append(rank++).append(" | ")
                    .append(safeText(stat.getMarket())).append(" | ")
                    .append(safeText(stat.getSecurityId())).append(" | ")
                    .append(safeLong(stat.getTotalFilledQty())).append(" | ")
                    .append(safeLong(stat.getOrderCount())).append(" | ")
                    .append(formatAmount(safeAmount(stat.getTradedAmount()))).append(" |")
                    .append(System.lineSeparator());
            }
            sb.append(System.lineSeparator());
        }

        sb.append("## Market Volume").append(System.lineSeparator()).append(System.lineSeparator());
        if (marketStats == null || marketStats.isEmpty()) {
            sb.append("No market data yet.").append(System.lineSeparator());
        } else {
            sb.append("| Market | Filled Qty | Order Count | Traded Amount |").append(System.lineSeparator());
            sb.append("| --- | ---: | ---: | ---: |").append(System.lineSeparator());
            for (OrderHistoryRepository.MarketVolumeStat stat : marketStats) {
                sb.append("| ")
                    .append(safeText(stat.getMarket())).append(" | ")
                    .append(safeLong(stat.getTotalFilledQty())).append(" | ")
                    .append(safeLong(stat.getOrderCount())).append(" | ")
                    .append(formatAmount(safeAmount(stat.getTradedAmount()))).append(" |")
                    .append(System.lineSeparator());
            }
        }

        return sb.toString();
    }

    private String buildFailureReport(Exception e) {
        String now = LocalDateTime.now().format(TIME_FORMATTER);
        StringBuilder sb = new StringBuilder(256);
        sb.append("# Trading System Startup Analysis").append(System.lineSeparator()).append(System.lineSeparator());
        sb.append("- Generated At: ").append(now).append(System.lineSeparator());
        sb.append("- Status: failed").append(System.lineSeparator());
        sb.append("- Reason: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append(System.lineSeparator());
        return sb.toString();
    }

    private Path resolveReportPath() {
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path detectedRoot = detectProjectRoot(cwd);
        return detectedRoot.resolve(REPORT_FILE_NAME);
    }

    private void writeReport(Path reportPath, String content) throws Exception {
        Files.writeString(
            reportPath,
            content,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    private Path detectProjectRoot(Path startPath) {
        Path current = startPath;
        for (int i = 0; i < 6 && current != null; i++) {
            if (isTradingSystemRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        return startPath;
    }

    private boolean isTradingSystemRoot(Path path) {
        if (path == null) return false;
        return Files.exists(path.resolve("pom.xml"))
            && Files.exists(path.resolve("trading-admin"))
            && Files.exists(path.resolve("trading-core"))
            && Files.exists(path.resolve("trading-gateway"));
    }

    private String formatAmount(BigDecimal amount) {
        return AMOUNT_FORMAT.format(amount);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private String safeText(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
