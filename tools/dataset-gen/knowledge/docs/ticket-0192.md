# 取数需求单 REQ-2025-0192

## 需求

管理层 2025-08-18 提单：会员等级粒度已支付订单明细金额 `paid_amount`（单位：元），按日汇总，报告期 2025-07，无筛选。需长期跟踪，请求固化为报表。

## 结论

`dws_trade_level_order_1d` 命中需求。取数字段：`stat_date`、`member_level`、`order_cnt`、`item_qty`、`paid_amount`；按 `stat_date` 卡 2025-07。口径：`paid_amount`、`item_qty` 可跨行相加；`order_cnt` 是去重下单数，不能累加，总下单数须回明细重算。考虑到长期跟踪，本次已交付，并沉淀为固定报表。
