-- 核心业务库 S2 扩展：在 core_m0.sql 的 5 张表之上新增 11 张表
-- 命名刻意不完全统一（uid / visitor_id / ord_no 指向同一类实体，ADR-005）
-- 数据由 gen/core_ext.py 用 INSERT...SELECT 从已有核心表派生，业务约束在构造时即成立

CREATE TABLE user_address (
  address_id     BIGINT       NOT NULL COMMENT '收货地址ID',
  user_id        BIGINT       NOT NULL COMMENT '用户ID',
  receiver_name  VARCHAR(50)  NOT NULL COMMENT '收货人姓名',
  province       VARCHAR(20)  NOT NULL COMMENT '省份',
  city           VARCHAR(20)  DEFAULT NULL COMMENT '城市',
  address_tag    VARCHAR(10)  NOT NULL COMMENT '地址标签：home-家，company-公司，other-其他',
  is_default     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认地址：1-是，0-否',
  created_time   DATETIME     NOT NULL COMMENT '创建时间',
  PRIMARY KEY (address_id),
  KEY idx_user_id (user_id),
  CONSTRAINT fk_address_user FOREIGN KEY (user_id) REFERENCES user_info (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址表';

CREATE TABLE item_brand (
  brand_id       INT          NOT NULL COMMENT '品牌ID',
  brand_name     VARCHAR(50)  NOT NULL COMMENT '品牌名称，与 item_sku.brand_name 对应',
  origin_country VARCHAR(20)  NOT NULL COMMENT '品牌所属国家或地区',
  founded_year   SMALLINT     NOT NULL COMMENT '品牌创立年份',
  brand_level    VARCHAR(10)  NOT NULL COMMENT '品牌等级：S、A、B',
  PRIMARY KEY (brand_id),
  UNIQUE KEY uk_brand_name (brand_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品品牌表';

CREATE TABLE mkt_coupon (
  coupon_id      INT           NOT NULL COMMENT '优惠券ID',
  coupon_name    VARCHAR(50)   NOT NULL COMMENT '优惠券名称',
  coupon_type    VARCHAR(20)   NOT NULL COMMENT '类型：full_reduction-满减，discount-折扣，no_threshold-无门槛',
  threshold_amt  DECIMAL(10,2) NOT NULL COMMENT '使用门槛金额（元），无门槛为0',
  face_value     DECIMAL(10,2) NOT NULL COMMENT '面额（元），折扣券为最高抵扣金额',
  valid_from     DATE          NOT NULL COMMENT '有效期开始',
  valid_to       DATE          NOT NULL COMMENT '有效期结束',
  PRIMARY KEY (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券定义表';

CREATE TABLE mkt_coupon_receive (
  receive_id     BIGINT        NOT NULL COMMENT '领券记录ID',
  coupon_id      INT           NOT NULL COMMENT '优惠券ID',
  user_id        BIGINT        NOT NULL COMMENT '领券用户ID',
  receive_time   DATETIME      NOT NULL COMMENT '领取时间',
  use_status     VARCHAR(10)   NOT NULL COMMENT '使用状态：used-已使用，unused-未使用',
  used_order_id  BIGINT        DEFAULT NULL COMMENT '使用该券的订单ID，未使用为空',
  use_time       DATETIME      DEFAULT NULL COMMENT '使用时间',
  PRIMARY KEY (receive_id),
  KEY idx_user_id (user_id),
  KEY idx_used_order_id (used_order_id),
  CONSTRAINT fk_receive_coupon FOREIGN KEY (coupon_id) REFERENCES mkt_coupon (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户领券记录表';

CREATE TABLE pay_order (
  pay_id         BIGINT        NOT NULL COMMENT '支付单ID',
  order_id       BIGINT        NOT NULL COMMENT '订单ID',
  uid            BIGINT        NOT NULL COMMENT '付款用户ID，即 user_info.user_id',
  pay_channel    VARCHAR(20)   NOT NULL COMMENT '支付渠道：alipay-支付宝，wechat-微信，card-银行卡，unionpay-云闪付',
  pay_amt        DECIMAL(12,2) NOT NULL COMMENT '支付金额（元），等于订单实付金额',
  pay_status     VARCHAR(10)   NOT NULL COMMENT '支付状态：success-成功',
  pay_time       DATETIME      NOT NULL COMMENT '支付完成时间',
  PRIMARY KEY (pay_id),
  UNIQUE KEY uk_order_id (order_id),
  KEY idx_pay_time (pay_time),
  CONSTRAINT fk_pay_order FOREIGN KEY (order_id) REFERENCES trade_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';

CREATE TABLE pay_refund (
  refund_id      BIGINT        NOT NULL COMMENT '退款单ID',
  order_id       BIGINT        NOT NULL COMMENT '订单ID',
  refund_amount  DECIMAL(12,2) NOT NULL COMMENT '退款金额（元），不超过订单实付金额',
  refund_reason  VARCHAR(20)   NOT NULL COMMENT '退款原因：quality-质量问题，wrong_item-发错货，no_longer_needed-不想要了，late_delivery-物流太慢',
  apply_time     DATETIME      NOT NULL COMMENT '申请时间',
  finish_time    DATETIME      NOT NULL COMMENT '退款完成时间',
  PRIMARY KEY (refund_id),
  UNIQUE KEY uk_order_id (order_id),
  CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES trade_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单表';

CREATE TABLE lgs_warehouse (
  warehouse_id   INT          NOT NULL COMMENT '仓库ID',
  warehouse_name VARCHAR(50)  NOT NULL COMMENT '仓库名称',
  region         VARCHAR(10)  NOT NULL COMMENT '所属大区：华东、华南、华北、华中、西南、西北',
  province       VARCHAR(20)  NOT NULL COMMENT '仓库所在省份',
  PRIMARY KEY (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库表';

CREATE TABLE lgs_shipment (
  ship_id        BIGINT       NOT NULL COMMENT '物流单ID',
  ord_no         BIGINT       NOT NULL COMMENT '订单号，即 trade_order.order_id',
  warehouse_id   INT          NOT NULL COMMENT '发货仓库ID',
  carrier        VARCHAR(20)  NOT NULL COMMENT '承运商：sf-顺丰，jd-京东，yto-圆通，zto-中通',
  ship_time      DATETIME     NOT NULL COMMENT '发货时间',
  sign_time      DATETIME     DEFAULT NULL COMMENT '签收时间，未签收为空',
  PRIMARY KEY (ship_id),
  UNIQUE KEY uk_ord_no (ord_no),
  KEY idx_warehouse_id (warehouse_id),
  CONSTRAINT fk_ship_warehouse FOREIGN KEY (warehouse_id) REFERENCES lgs_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流发货单表';

CREATE TABLE aftersale_order (
  aftersale_id   BIGINT       NOT NULL COMMENT '售后单ID',
  order_item_id  BIGINT       NOT NULL COMMENT '订单明细ID，即 trade_order_item.id',
  aftersale_type VARCHAR(10)  NOT NULL COMMENT '售后类型：return-退货，exchange-换货，repair-维修',
  apply_time     DATETIME     NOT NULL COMMENT '申请时间，晚于签收时间',
  status         VARCHAR(10)  NOT NULL COMMENT '处理状态：processing-处理中，closed-已完结，rejected-已驳回',
  PRIMARY KEY (aftersale_id),
  KEY idx_order_item_id (order_item_id),
  CONSTRAINT fk_aftersale_item FOREIGN KEY (order_item_id) REFERENCES trade_order_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单表';

CREATE TABLE item_review (
  review_id      BIGINT       NOT NULL COMMENT '评价ID',
  order_item_id  BIGINT       NOT NULL COMMENT '订单明细ID',
  sku_id         BIGINT       NOT NULL COMMENT '商品SKU ID',
  user_id        BIGINT       NOT NULL COMMENT '评价用户ID',
  rating         TINYINT      NOT NULL COMMENT '评分：1～5星',
  has_image      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否带图：1-是，0-否',
  review_time    DATETIME     NOT NULL COMMENT '评价时间，晚于签收时间',
  PRIMARY KEY (review_id),
  UNIQUE KEY uk_order_item_id (order_item_id),
  KEY idx_sku_id (sku_id),
  CONSTRAINT fk_review_item FOREIGN KEY (order_item_id) REFERENCES trade_order_item (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价表';

CREATE TABLE log_visit (
  visit_id       BIGINT       NOT NULL COMMENT '访问记录ID',
  visitor_id     BIGINT       NOT NULL COMMENT '访问用户ID，即 user_info.user_id',
  sku_id         BIGINT       NOT NULL COMMENT '浏览的商品SKU ID',
  channel        VARCHAR(20)  NOT NULL COMMENT '访问渠道：app、mini_program-小程序、web',
  visit_time     DATETIME     NOT NULL COMMENT '访问时间',
  stay_seconds   INT          NOT NULL COMMENT '页面停留秒数',
  PRIMARY KEY (visit_id),
  KEY idx_visitor_id (visitor_id),
  KEY idx_visit_time (visit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品页访问日志';
