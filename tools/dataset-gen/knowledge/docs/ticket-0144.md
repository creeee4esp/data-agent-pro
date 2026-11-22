# 取数需求单 REQ-2025-0144

## 需求

物流部于 2025-06-16 正式提出取数申请，需求为用户粒度下已支付订单的明细金额 `paid_amount`（单位：元），按日汇总，报告期为 2025-06，不设任何筛选条件。此数据拟用于核对当月各买家的支付贡献，供部门内部参考。

## 结论

`dws_trade_user_order_1d` 为本需求的推荐用表，取数涉及 `stat_date`、`buyer_id`、`order_cnt`、`paid_order_cnt`、`paid_amount` 等字段，以 `stat_date` 限定 2025-06 范围后按 `buyer_id` 输出即可。需特别说明：`paid_amount` 为可加和指标，跨日或跨用户求和均成立；而 `order_cnt` 与 `paid_order_cnt` 属于去重口径，不得跨行相加，如需整月订单总量应另行计算。本需求已交付，并随附 SQL 供物流部自助复用。
