# 取数需求单 REQ-2025-0263

## 需求

物流部 2025-11-04 提的，想要 2025-03 至 2025-05 的已支付订单明细金额（`paid_amount`），按一级品类粒度、日度下单口径拉，没别的筛选。单号 REQ-2025-0263，流量数据组接的。

## 结论

`dws_trade_cate1_order_1d`（DWS-一级品类粒度下单汇总（日））这张表够用，字段用 `stat_date`、`category1_id`、`category1_name`、`order_cnt`、`item_qty`、`paid_amount`，日期 `stat_date` 卡在 2025-03 到 2025-05。

加和这块：`paid_amount` 和 `item_qty` 可跨行相加，各品类、各天的 `paid_amount` 加起来算区间总额没问题。`order_cnt` 不可跨行相加，涉及下单数别逐行求和，会重复计数。

这次已交付一次性取数结果。
