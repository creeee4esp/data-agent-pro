# 取数需求单 REQ-2025-0119

## 需求

财务部需 2025-03 至 2025-05、按用户粒度的日度已支付明细金额 `paid_amount`（元），无筛选，用于用户支付贡献的季度核算。

- 时间范围：2025-03 至 2025-05
- 维度：用户
- 核心指标：已支付明细金额 `paid_amount`

## 结论

`dws_trade_user_order_1d` 对口，为用户粒度的按日交易汇总表。

- 取数字段：`stat_date`、`buyer_id`、`order_cnt`、`paid_order_cnt`、`paid_amount`
- 时间范围：`stat_date` 限定 2025-03 至 2025-05
- 可加：`paid_amount` 可跨行相加，按用户跨日累加成期间金额成立
- 注意：`order_cnt`、`paid_order_cnt` 均不可相加，跨行累加会重复计数
- 交付：已交付一次性取数结果
