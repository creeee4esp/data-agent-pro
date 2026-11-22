# 取数需求单 REQ-2025-0295

## 需求

物流部于 2025-12-25 提出：`paid_amount`（已支付订单的明细金额（元）），会员等级下单月汇总，时间范围 2025-05，无筛选。

## 结论

`dws_trade_level_order_1m` 为交付用表，商品数据组出数。字段：`stat_date`、`member_level`、`order_cnt`、`item_qty`、`paid_amount`。

### 口径

`paid_amount`、`item_qty` 可跨行相加；`order_cnt` 不可跨行相加，跨会员等级汇总会重复计数，需总下单数时回明细去重。

### 交付

已交付，并沉淀为固定报表。
