# 取数需求单 REQ-2025-0186

## 需求

管理层 2025-08-07 提单：一级品类粒度已支付订单明细金额 `paid_amount`（单位：元），按月汇总，报告期 2025-07，无筛选。需后续复用。

## 结论

`dws_trade_cate1_order_1m` 命中需求。取数字段：`stat_date`、`category1_id`、`category1_name`、`order_cnt`、`item_qty`、`paid_amount`；按 `stat_date` 卡 2025-07。口径：`paid_amount`、`item_qty` 可跨行相加；`order_cnt` 是去重下单数，不能累加，总下单数须回明细重算。本次已交付，并附 SQL 供管理层自助复用。
