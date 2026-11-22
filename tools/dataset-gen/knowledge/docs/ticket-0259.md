# 取数需求单 REQ-2025-0259

## 需求

市场部在 2025-11-03 提出取数，关注 2025-08 至 2025-10 的退款金额（`refund_amount`），希望按收货省份粒度、月度退款口径查看，并且只看浙江省。本单编号 REQ-2025-0259，处理人流量数据组。市场部想单独追踪浙江省这三个月的退款金额变化。

## 结论

`dws_trade_prov_refund_1m` 即 DWS-收货省份粒度退款汇总（月）对应本次取数，取 `stat_date`、`receiver_province`、`refund_cnt`、`refund_amount`，报告期 `stat_date` 覆盖 2025-08 至 2025-10，并按 `receiver_province` 只保留浙江省。

加和方面较为直观：`refund_amount` 与 `refund_cnt` 都可跨行相加，把浙江省这三个月的 `refund_amount` 逐月累加得到区间总退款金额是准确的，也可按月对比观察走势。

考虑到该省份需持续跟踪，本次已交付，并附 SQL 供需求方自助复用。
