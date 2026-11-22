# 取数需求单 REQ-2025-0101

## 需求

物流部现提出取数需求，拟获取 2025-01 至 2025-03 期间收货省份为福建省的按日退款笔数 `refund_cnt`，筛选条件为只看福建省，用于该省售后压力的专项评估。

## 结论

经评估，`dws_trade_prov_refund_1d` 系收货省份粒度的按日退款汇总表，可满足本次要求。所取字段为 `stat_date`、`receiver_province`、`refund_cnt`、`refund_amount`，并以 `receiver_province` 等于福建省作过滤，时间范围依 `stat_date` 限定于 2025-01 至 2025-03。

经核验口径，退款笔数 `refund_cnt` 与退款金额 `refund_amount` 均可跨行相加，故在已限定福建省的前提下，按日累加以得到该省各月或整季度的退款合计，均属可行，不致重复计数。

本需求已交付，附 SQL 供需求方自助复用。
