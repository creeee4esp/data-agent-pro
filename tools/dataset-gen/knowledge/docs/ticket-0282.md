# 取数需求单 REQ-2025-0282

## 需求

市场部 2025-12-01 提的，想要 2025-01 至 2025-03 的退款金额（`refund_amount`），按退款原因粒度、日度退款口径拉，没别的筛选。单号 REQ-2025-0282，交易数据组接的。

## 结论

`dws_trade_reason_refund_1d`（DWS-退款原因粒度退款汇总（日））这张表够用，字段用 `stat_date`、`refund_reason`、`refund_cnt`、`refund_amount`，日期 `stat_date` 卡在 2025-01 到 2025-03。

加和这块省心：`refund_cnt` 和 `refund_amount` 都可跨行相加，把各退款原因、各天的 `refund_amount` 加起来算区间总退款金额，再按原因排个序找主因都没问题。

这次已交付，附 SQL 供需求方自助复用。
