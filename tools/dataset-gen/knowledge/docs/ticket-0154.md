# 取数需求单 REQ-2025-0154

## 需求

市场部 2025-06-30 提单，要收货省份维度的已支付订单明细金额 `paid_amount`（单位：元），按日汇总，报告期就看 2025-05 一个月，不加筛选。想看看各省当月的成交金额分布。

## 结论

`dws_trade_prov_order_1d` 这张表对口，取数用到 `stat_date`、`receiver_province`、`order_cnt`、`item_qty`、`paid_amount` 这几个字段，按 `stat_date` 卡 2025-05 就行。提醒一句：`paid_amount` 和 `item_qty` 是可加和的，按省或按月汇总直接相加没问题；但 `order_cnt` 是去重下单数，不能跨行累加，各省相加不等于全国总下单数。本次已交付一次性取数结果。
