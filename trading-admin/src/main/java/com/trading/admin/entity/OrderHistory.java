package com.trading.admin.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 订单历史表，由 Core 推送事件维护；用于按股东号、状态查询历史订单。
 */
@Entity
@Table(name = "t_order_history", indexes = {
    @Index(name = "idx_shareholder_id", columnList = "shareholder_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_cl_order_id", columnList = "cl_order_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class OrderHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cl_order_id", nullable = false, length = 64)
    private String clOrderId;

    @Column(name = "engine_order_id")
    private Long engineOrderId;

    @Column(name = "shareholder_id", nullable = false, length = 32)
    private String shareholderId;

    @Column(name = "market", length = 8)
    private String market;

    @Column(name = "security_id", length = 16)
    private String securityId;

    @Column(name = "side", length = 4)
    private String side;

    @Column(name = "price")
    private Double price;

    @Column(name = "order_qty")
    private Integer orderQty;

    @Column(name = "filled_qty")
    private Integer filledQty = 0;

    @Column(name = "canceled_qty")
    private Integer canceledQty = 0;

    /** LIVE=挂单中, PARTIALLY_FILLED=部分成交, FILLED=全部成交, CANCELLED=已撤单 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getClOrderId() { return clOrderId; }
    public void setClOrderId(String clOrderId) { this.clOrderId = clOrderId; }
    public Long getEngineOrderId() { return engineOrderId; }
    public void setEngineOrderId(Long engineOrderId) { this.engineOrderId = engineOrderId; }
    public String getShareholderId() { return shareholderId; }
    public void setShareholderId(String shareholderId) { this.shareholderId = shareholderId; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getSecurityId() { return securityId; }
    public void setSecurityId(String securityId) { this.securityId = securityId; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getOrderQty() { return orderQty; }
    public void setOrderQty(Integer orderQty) { this.orderQty = orderQty; }
    public Integer getFilledQty() { return filledQty; }
    public void setFilledQty(Integer filledQty) { this.filledQty = filledQty; }
    public Integer getCanceledQty() { return canceledQty; }
    public void setCanceledQty(Integer canceledQty) { this.canceledQty = canceledQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
