package com.trading.admin.repository;

import com.trading.admin.entity.OrderHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {

    List<OrderHistory> findByShareholderIdOrderByCreatedAtDesc(String shareholderId, Pageable pageable);

    List<OrderHistory> findByShareholderIdAndStatusOrderByCreatedAtDesc(String shareholderId, String status, Pageable pageable);

    List<OrderHistory> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    List<OrderHistory> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Optional<OrderHistory> findByClOrderId(String clOrderId);

    List<OrderHistory> findByShareholderIdAndCreatedAtBetween(String shareholderId,
                                                              LocalDateTime start,
                                                              LocalDateTime end);
}
