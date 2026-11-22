# 取数需求单 REQ-2025-0270

## 需求

财务部 2025-11-15 提的，想要 2025-09 的已支付订单明细金额（`paid_amount`），按一级品类粒度、月度下单口径拉一份，没有额外筛选。单号 REQ-2025-0270，流量数据组接的。

## 结论

`dws_trade_cate1_order_1m`（DWS-一级品类粒度下单汇总（月））这张表够用，字段用 `stat_date`、`category1_id`、`category1_name`、`order_cnt`、`item_qty`、`paid_amount`，日期 `stat_date` 取 2025-09。

加和这块：`paid_amount` 和 `item_qty` 可跨行相加，各品类的 `paid_amount` 加起来算当月总额没问题。`order_cnt` 不可跨行相加，涉及下单数别直接求和。

这次已交付，附 SQL 供需求方自助复用。
