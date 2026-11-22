# 取数需求单 REQ-2025-0229

## 需求

物流部 2025-10-01 提的需求，想要 2025-09 的支付订单数（`paid_order_cnt`），按用户粒度、日度交易口径拉一份，没有额外筛选。单号 REQ-2025-0229，用户数据组接的。

## 结论

`dws_trade_user_order_1d`（DWS-用户粒度交易汇总（日））这张表能满足，取 `stat_date`、`buyer_id`、`order_cnt`、`paid_order_cnt`、`paid_amount`，日期 `stat_date` 卡在 2025-09。

有个口径得提前说一下：`paid_order_cnt` 和 `order_cnt` 都不可跨行相加，别把各用户或各天的 `paid_order_cnt` 加起来当合计，那样会重复计数。要加和的话用 `paid_amount`，它可以跨行相加。

这次已交付一次性取数结果。
