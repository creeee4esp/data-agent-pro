-- 核心业务库 M0：5 张表
-- 命名刻意不完全统一（buyer_id/user_id、discount_amt/pay_amount、qty、cate_level），见 ADR-005

CREATE TABLE user_info (
  user_id        BIGINT       NOT NULL COMMENT '用户ID',
  user_name      VARCHAR(50)  NOT NULL COMMENT '用户姓名',
  gender         CHAR(1)      NOT NULL DEFAULT 'U' COMMENT '性别：M-男，F-女，U-未知',
  birth_year     SMALLINT     DEFAULT NULL COMMENT '出生年份',
  province       VARCHAR(20)  NOT NULL COMMENT '常住省份，如 浙江省、上海市',
  city           VARCHAR(20)  DEFAULT NULL COMMENT '常住城市',
  member_level   TINYINT      NOT NULL DEFAULT 1 COMMENT '会员等级：1-普通，2-白银，3-黄金，4-铂金，5-钻石',
  register_time  DATETIME     NOT NULL COMMENT '注册时间',
  PRIMARY KEY (user_id),
  KEY idx_register_time (register_time),
  KEY idx_province (province)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基础信息表';

CREATE TABLE item_category (
  category_id    INT          NOT NULL COMMENT '品类ID',
  category_name  VARCHAR(50)  NOT NULL COMMENT '品类名称',
  parent_id      INT          DEFAULT NULL COMMENT '父品类ID，一级品类为空',
  cate_level     TINYINT      NOT NULL COMMENT '品类层级：1-一级品类，2-二级品类',
  PRIMARY KEY (category_id),
  KEY idx_parent_id (parent_id),
  CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES item_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品品类表（两级）';

CREATE TABLE item_sku (
  sku_id         BIGINT        NOT NULL COMMENT '商品SKU ID',
  sku_name       VARCHAR(100)  NOT NULL COMMENT '商品名称',
  category_id    INT           NOT NULL COMMENT '所属二级品类ID',
  brand_name     VARCHAR(50)   NOT NULL COMMENT '品牌名称',
  price          DECIMAL(10,2) NOT NULL COMMENT '当前售价（元）',
  stock          INT           NOT NULL COMMENT '当前库存数量',
  sku_status     VARCHAR(20)   NOT NULL COMMENT '商品状态：on_sale-在售，off_shelf-已下架',
  created_time   DATETIME      NOT NULL COMMENT '上架时间',
  PRIMARY KEY (sku_id),
  KEY idx_category_id (category_id),
  CONSTRAINT fk_sku_category FOREIGN KEY (category_id) REFERENCES item_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

CREATE TABLE trade_order (
  order_id           BIGINT        NOT NULL COMMENT '订单ID',
  buyer_id           BIGINT        NOT NULL COMMENT '下单用户ID，关联 user_info.user_id',
  order_status       VARCHAR(20)   NOT NULL COMMENT '订单状态：created-待支付，cancelled-已取消，paid-已支付，shipped-已发货，completed-已完成，refunded-已退款',
  total_amount       DECIMAL(12,2) NOT NULL COMMENT '订单商品总金额（元）= 明细金额之和',
  discount_amt       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '优惠抵扣金额（元）',
  pay_amount         DECIMAL(12,2) NOT NULL COMMENT '应付/实付金额（元）= 商品总金额 - 优惠',
  receiver_province  VARCHAR(20)   NOT NULL COMMENT '收货省份',
  order_time         DATETIME      NOT NULL COMMENT '下单时间',
  pay_time           DATETIME      DEFAULT NULL COMMENT '支付时间，未支付为空',
  PRIMARY KEY (order_id),
  KEY idx_buyer_id (buyer_id),
  KEY idx_order_time (order_time),
  CONSTRAINT fk_order_buyer FOREIGN KEY (buyer_id) REFERENCES user_info (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单主表';

CREATE TABLE trade_order_item (
  id            BIGINT        NOT NULL COMMENT '明细ID',
  order_id      BIGINT        NOT NULL COMMENT '订单ID',
  sku_id        BIGINT        NOT NULL COMMENT '商品SKU ID',
  qty           INT           NOT NULL COMMENT '购买件数',
  unit_price    DECIMAL(10,2) NOT NULL COMMENT '成交单价（元）',
  item_amount   DECIMAL(12,2) NOT NULL COMMENT '明细金额（元）= 件数 × 成交单价',
  PRIMARY KEY (id),
  KEY idx_order_id (order_id),
  KEY idx_sku_id (sku_id),
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES trade_order (order_id),
  CONSTRAINT fk_item_sku FOREIGN KEY (sku_id) REFERENCES item_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交易订单明细表';
