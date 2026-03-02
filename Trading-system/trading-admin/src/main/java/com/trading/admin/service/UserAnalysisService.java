package com.trading.admin.service;

import com.trading.admin.dto.UserAnalysisDTO;
import com.trading.admin.entity.OrderHistory;
import com.trading.admin.entity.User;
import com.trading.admin.repository.OrderHistoryRepository;
import com.trading.admin.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserAnalysisService {

    private final OrderHistoryRepository orderHistoryRepository;
    private final UserRepository userRepository;

    public UserAnalysisService(OrderHistoryRepository orderHistoryRepository,
                               UserRepository userRepository) {
        this.orderHistoryRepository = orderHistoryRepository;
        this.userRepository = userRepository;
    }

    public UserAnalysisDTO analyze(Long userId,
                                   LocalDateTime start,
                                   LocalDateTime end) {

        User user = userRepository.findByUid(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在, uid=" + userId));

        String shareholderId = user.getShareholderId();

        List<OrderHistory> orders =
                orderHistoryRepository.findByShareholderIdAndCreatedAtBetween(shareholderId, start, end);

        UserAnalysisDTO dto = new UserAnalysisDTO();

        long total = orders.size();
        long filledOrders = orders.stream()
                .filter(o -> "FILLED".equals(o.getStatus()))
                .count();

        long cancelled = orders.stream()
                .filter(o -> "CANCELLED".equals(o.getStatus()))
                .count();

        BigDecimal turnover = orders.stream()
                .filter(o -> o.getFilledQty() != null && o.getFilledQty() > 0)
                .map(o -> {
                    double price = o.getPrice() != null ? o.getPrice() : 0.0d;
                    int qty = o.getFilledQty() != null ? o.getFilledQty() : 0;
                    return BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalFilledQty = orders.stream()
                .filter(o -> o.getFilledQty() != null)
                .mapToLong(o -> o.getFilledQty())
                .sum();

        BigDecimal avgPrice;
        if (totalFilledQty == 0L) {
            avgPrice = BigDecimal.ZERO;
        } else {
            avgPrice = turnover.divide(BigDecimal.valueOf(totalFilledQty), 4, RoundingMode.HALF_UP);
        }

        dto.setTotalOrders(total);
        dto.setFilledOrders(filledOrders);
        dto.setCancelledOrders(cancelled);
        dto.setTotalTurnover(turnover);
        dto.setAvgPrice(avgPrice);

        return dto;
    }
}