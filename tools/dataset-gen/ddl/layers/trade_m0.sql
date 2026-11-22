-- M0 分层派生：交易域 DWD -> DWS -> ADS，全部从 ODS 计算（ADR-003）
-- GMV 口径：已支付过的订单（pay_time 非空，含后续退款）的明细金额之和，按下单日期统计

DROP TABLE IF EXISTS dwd_trade_order_detail_di;
CREATE TABLE dwd_trade_order_detail_di (
  id                  BIGINT        NOT NULL COMMENT '明细ID',
  order_id            BIGINT        NOT NULL COMMENT '订单ID',
  buyer_id            BIGINT        NOT NULL COMMENT '买家用户ID',
  member_level        TINYINT       DEFAULT NULL COMMENT '买家会员等级',
  receiver_province   VARCHAR(20)   DEFAULT NULL COMMENT '收货省份',
  sku_id              BIGINT        NOT NULL COMMENT '商品SKU ID',
  sku_name            VARCHAR(100)  DEFAULT NULL COMMENT '商品名称',
  brand_name          VARCHAR(50)   DEFAULT NULL COMMENT '品牌',
  category2_id        INT           DEFAULT NULL COMMENT '二级品类ID',
  category2_name      VARCHAR(50)   DEFAULT NULL COMMENT '二级品类名称',
  category1_id        INT           DEFAULT NULL COMMENT '一级品类ID',
  category1_name      VARCHAR(50)   DEFAULT NULL COMMENT '一级品类名称',
  qty                 INT           NOT NULL COMMENT '购买件数',
  unit_price          DECIMAL(10,2) NOT NULL COMMENT '成交单价（元）',
  item_amount         DECIMAL(12,2) NOT NULL COMMENT '明细金额（元）',
  order_status        VARCHAR(20)   NOT NULL COMMENT '订单状态',
  is_paid             TINYINT       NOT NULL COMMENT '是否已支付：1-是，0-否',
  order_time          DATETIME      NOT NULL COMMENT '下单时间',
  order_date          DATE          NOT NULL COMMENT '下单日期',
  dt                  DATE          NOT NULL COMMENT '分区日期',
  PRIMARY KEY (id),
  KEY idx_order_date (order_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DWD-交易订单明细宽表';

INSERT INTO dwd_trade_order_detail_di
SELECT i.id, i.order_id, o.buyer_id, u.member_level, o.receiver_province,
       i.sku_id, s.sku_name, s.brand_name,
       c2.category_id, c2.category_name, c1.category_id, c1.category_name,
       i.qty, i.unit_price, i.item_amount, o.order_status,
       IF(o.pay_time IS NULL, 0, 1), o.order_time, DATE(o.order_time), i.dt
FROM ods_trade_order_item_di i
JOIN ods_trade_order_di o ON o.order_id = i.order_id
LEFT JOIN ods_user_info_di u ON u.user_id = o.buyer_id
LEFT JOIN ods_item_sku_di s ON s.sku_id = i.sku_id
LEFT JOIN ods_item_category_di c2 ON c2.category_id = s.category_id
LEFT JOIN ods_item_category_di c1 ON c1.category_id = c2.parent_id;

DROP TABLE IF EXISTS dws_trade_user_order_1d;
CREATE TABLE dws_trade_user_order_1d (
  stat_date        DATE          NOT NULL COMMENT '统计日期',
  buyer_id         BIGINT        NOT NULL COMMENT '买家用户ID',
  order_cnt        INT           NOT NULL COMMENT '下单数',
  paid_order_cnt   INT           NOT NULL COMMENT '支付订单数',
  paid_amount      DECIMAL(14,2) NOT NULL COMMENT '支付订单明细金额',
  PRIMARY KEY (stat_date, buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DWS-用户粒度交易汇总（日）';

INSERT INTO dws_trade_user_order_1d
SELECT order_date, buyer_id, COUNT(DISTINCT order_id),
       COUNT(DISTINCT IF(is_paid = 1, order_id, NULL)),
       SUM(IF(is_paid = 1, item_amount, 0))
FROM dwd_trade_order_detail_di
GROUP BY order_date, buyer_id;

DROP TABLE IF EXISTS ads_trade_gmv_daily;
CREATE TABLE ads_trade_gmv_daily (
  stat_date        DATE          NOT NULL COMMENT '统计日期',
  gmv              DECIMAL(16,2) NOT NULL COMMENT 'GMV：已支付订单明细金额之和（元）',
  paid_order_cnt   INT           NOT NULL COMMENT '支付订单数',
  paid_buyer_cnt   INT           NOT NULL COMMENT '支付买家数',
  PRIMARY KEY (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ADS-每日GMV报表';

INSERT INTO ads_trade_gmv_daily
SELECT stat_date, SUM(paid_amount), SUM(paid_order_cnt), SUM(IF(paid_order_cnt > 0, 1, 0))
FROM dws_trade_user_order_1d
GROUP BY stat_date;
