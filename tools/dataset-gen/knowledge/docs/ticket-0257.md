# 取数需求单 REQ-2025-0257

## 需求

客服部 2025-10-30 提的，想要 2025-06 的下单数（`order_cnt`），按二级品类粒度、日度下单口径拉一份，没有额外筛选。单号 REQ-2025-0257，物流数据组接的。

## 结论

`dws_trade_cate2_order_1d`（DWS-二级品类粒度下单汇总（日））这张表够用，字段用 `stat_date`、`category2_id`、`category2_name`、`order_cnt`、`item_qty`、`paid_amount`，日期 `stat_date` 取 2025-06。

加和这块要提醒：`order_cnt` 不可跨行相加，别把各二级品类或各天的 `order_cnt` 加起来当合计，会重复计数。想加和就用 `item_qty` 和 `paid_amount`，这两个跨行相加没问题。

这次已交付一次性取数结果。
