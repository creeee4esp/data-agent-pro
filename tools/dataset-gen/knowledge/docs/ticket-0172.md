# 取数需求单 REQ-2025-0172

## 需求

管理层于 2025-07-26 正式提出取数申请，需求对象为买家用户粒度的下单数 `order_cnt`，按月汇总，报告期限定为单月 2025-03，不附加任何筛选条件。该数据拟用于观察当月各买家的下单频次分布。

## 结论

`dws_trade_user_order_1m` 为本需求推荐用表，取数涉及 `stat_date`、`buyer_id`、`order_cnt`、`item_qty`、`paid_amount` 等字段，以 `stat_date` 限定 2025-03 后按 `buyer_id` 输出即可。需特别提请注意：`order_cnt` 系去重下单数，不得跨行累加，若将各买家的 `order_cnt` 相加将导致重复计数；`item_qty` 与 `paid_amount` 则为可加和指标，汇总无碍。本需求已交付一次性取数结果。
