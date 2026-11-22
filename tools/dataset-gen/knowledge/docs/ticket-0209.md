# 取数需求单 REQ-2025-0209

## 需求

财务部 2025-08-29 提单：品牌粒度购买件数 `item_qty`，按月汇总，报告期 2025-07，无筛选。

## 结论

`dws_trade_brand_order_1m` 命中需求。取数字段：`stat_date`、`brand_name`、`order_cnt`、`item_qty`、`paid_amount`；按 `stat_date` 卡 2025-07。口径：`item_qty`、`paid_amount` 可跨行相加，按品牌汇总成立；`order_cnt` 是去重下单数，不能累加，总下单数须回明细重算。本次已交付一次性取数结果。
