package com.trading.admin.repository;

import com.trading.admin.entity.OrderHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {

    List<OrderHistory> findByShareholderIdOrderByCreatedAtDesc(String shareholderId, Pageable pageable);

    List<OrderHistory> findByShareholderIdAndStatusOrderByCreatedAtDesc(String shareholderId, String status, Pageable pageable);

    List<OrderHistory> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<OrderHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<OrderHistory> findByClOrderId(String clOrderId);

    List<OrderHistory> findByClOrderIdIn(List<String> clOrderIds);

    @Query(value = "SELECT COUNT(*) FROM t_order_history", nativeQuery = true)
    long countAllOrders();

    @Query(value = "SELECT COALESCE(SUM(filled_qty), 0) FROM t_order_history", nativeQuery = true)
    long sumFilledQty();

    @Query(value = "SELECT COALESCE(SUM(filled_qty * price), 0) FROM t_order_history", nativeQuery = true)
    BigDecimal sumTradedAmount();

    @Query(value = """
        SELECT
            market AS market,
            security_id AS securityId,
            COALESCE(SUM(filled_qty), 0) AS totalFilledQty,
            COUNT(*) AS orderCount,
            COALESCE(SUM(filled_qty * price), 0) AS tradedAmount
        FROM t_order_history
        WHERE security_id IS NOT NULL AND security_id <> ''
        GROUP BY market, security_id
        ORDER BY totalFilledQty DESC, orderCount DESC
        LIMIT 10
        """, nativeQuery = true)
    List<StockHotStat> findTopStocksByFilledQty();

    @Query(value = """
        SELECT
            market AS market,
            COALESCE(SUM(filled_qty), 0) AS totalFilledQty,
            COUNT(*) AS orderCount,
            COALESCE(SUM(filled_qty * price), 0) AS tradedAmount
        FROM t_order_history
        WHERE market IS NOT NULL AND market <> ''
        GROUP BY market
        ORDER BY totalFilledQty DESC, orderCount DESC
        """, nativeQuery = true)
    List<MarketVolumeStat> findMarketVolumeStats();

    interface StockHotStat {
        String getMarket();
        String getSecurityId();
        Long getTotalFilledQty();
        Long getOrderCount();
        BigDecimal getTradedAmount();
    }

    interface MarketVolumeStat {
        String getMarket();
        Long getTotalFilledQty();
        Long getOrderCount();
        BigDecimal getTradedAmount();
    }
}
