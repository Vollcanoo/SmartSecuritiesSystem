-- schema.sql
CREATE DATABASE IF NOT EXISTS trading_admin DEFAULT CHARACTER SET utf8mb4;


USE trading_admin;

-- 用户表
CREATE TABLE t_user (
                        id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        uid BIGINT NOT NULL UNIQUE COMMENT '内部用户ID',
                        shareholder_id VARCHAR(10) NOT NULL COMMENT '股东号',
                        username VARCHAR(50) NOT NULL UNIQUE,
                        status TINYINT DEFAULT 1 COMMENT '1:正常 0:禁用',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        INDEX idx_shareholder_id (shareholder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';



-- 成交表
CREATE TABLE t_trade (
                         id BIGINT PRIMARY KEY AUTO_INCREMENT,
                         trade_id BIGINT NOT NULL UNIQUE COMMENT '成交ID',
                         exec_id VARCHAR(12) NOT NULL COMMENT '执行ID',
                         order_id BIGINT NOT NULL COMMENT '订单ID',
                         cl_order_id VARCHAR(16) NOT NULL COMMENT '客户订单ID',
                         uid BIGINT NOT NULL COMMENT '用户ID',
                         symbol_id INT NOT NULL COMMENT '证券ID',
                         side TINYINT NOT NULL COMMENT '1:买 2:卖',
                         exec_price DECIMAL(12,4) NOT NULL COMMENT '成交价格',
                         exec_qty BIGINT NOT NULL COMMENT '成交数量',
                         counter_order_id BIGINT COMMENT '对手方订单ID',
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                         INDEX idx_order_id (order_id),
                         INDEX idx_symbol_created (symbol_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成交记录表';

-- ID映射表
CREATE TABLE t_id_mapping (
                              id BIGINT PRIMARY KEY AUTO_INCREMENT,
                              mapping_type VARCHAR(20) NOT NULL COMMENT 'ORDER/SYMBOL/USER',
                              external_id VARCHAR(50) NOT NULL COMMENT '外部ID',
                              internal_id BIGINT NOT NULL COMMENT '内部ID',
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                              UNIQUE KEY uk_type_external (mapping_type, external_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ID映射表';