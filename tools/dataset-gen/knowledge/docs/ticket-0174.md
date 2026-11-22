# 取数需求单 REQ-2025-0174

## 需求

管理层 2025-07-27 提单，想看一级品类维度的已支付订单明细金额 `paid_amount`（单位：元），按月汇总，时间范围 2025-02 到 2025-04，不加筛选。主要是想看一季度各一级品类的成交金额走势。

## 结论

`dws_trade_cate1_order_1m` 这张表对口，取数用到 `stat_date`、`category1_id`、`category1_name`、`order_cnt`、`item_qty`、`paid_amount` 这几个字段，按 `stat_date` 卡 2025-02 至 2025-04 就行。提醒一下：`paid_amount` 和 `item_qty` 是可加和的，按品类或按月汇总直接相加都没问题；但 `order_cnt` 是去重下单数，不能跨行累加，想看总下单数得回明细重算。本次已交付一次性取数结果。
