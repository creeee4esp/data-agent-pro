# 取数需求单 REQ-2025-0121

## 需求

客服部需 2025-05、各一级品类按周的下单数 `order_cnt`，无筛选，用于品类成交的周度观察。

- 时间范围：2025-05
- 维度：一级品类
- 核心指标：下单数 `order_cnt`

## 结论

`dws_trade_cate1_order_1w` 对口，为一级品类粒度的按周下单汇总表。

- 取数字段：`stat_date`、`category1_id`、`category1_name`、`order_cnt`、`item_qty`、`paid_amount`
- 时间范围：`stat_date` 限定 2025-05
- 口径：`order_cnt` 不可跨行相加，跨周或多品类累加会重复计数
- 建议：如需累计规模，改用可加的 `item_qty`、`paid_amount`
- 交付：已交付，附 SQL 供需求方自助复用
