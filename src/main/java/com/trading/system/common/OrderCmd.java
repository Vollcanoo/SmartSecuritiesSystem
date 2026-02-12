package com.trading.system.common;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderCmd {
    // 这是一个标准的数据传输对象 (DTO)
    private String type;           // NEW(下单), CANCEL(撤单)
    private String clOrderId;      // 客户端生成的唯一ID
    private String shareholderId;  // 股东代码 (核心风控字段)
    private String securityId;     // 股票代码 (如 600030)
    private String side;           // B(买), S(卖)
    private BigDecimal price;      // 价格
    private Integer qty;           // 数量
    private Long timestamp;        // 时间戳
}
