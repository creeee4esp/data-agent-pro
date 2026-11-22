# 取数需求单 REQ-2025-0289

## 需求

物流部于 2025-12-18 提出需求：`order_cnt`（下单数），按一级品类粒度的下单月汇总，时间范围 2025-08 至 2025-09，无筛选。

## 结论

`dws_trade_cate1_order_1m` 为交付用表，商品数据组出数。字段：`stat_date`、`category1_id`、`category1_name`、`order_cnt`、`item_qty`、`paid_amount`。

### 口径提示

`order_cnt` 不可跨行相加，跨品类或跨月汇总会把同一订单重复计数；`item_qty`、`paid_amount` 可跨行相加。需要总下单数时应回明细去重。

### 交付

已交付，附 SQL 供物流部自助复用。
