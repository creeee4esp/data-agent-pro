# 取数需求单 REQ-2025-0200

## 需求

商品部于 2025-08-24 提出的需求，围绕收货省份粒度的已支付订单明细金额 `paid_amount`（单位：元）展开，按日汇总，报告期为单月 2025-07，并要求只看上海市。从背景看，商品部想单独观察上海市当月的成交金额表现，用于区域选品的参考。

## 结论

`dws_trade_prov_order_1d` 对应这一诉求，取数落在 `stat_date`、`receiver_province`、`order_cnt`、`item_qty`、`paid_amount` 这几个字段上，以 `stat_date` 限定 2025-07、并在 `receiver_province` 上过滤上海市后输出即可。分析时要留意口径差异：`paid_amount` 与 `item_qty` 是可加和指标，跨日求和成立；而 `order_cnt` 是去重下单数，不能跨行累加，若要看整月的总下单数须回明细重算。本次已交付一次性取数结果。
