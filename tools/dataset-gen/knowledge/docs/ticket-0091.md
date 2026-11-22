# 取数需求单 REQ-2025-0091

## 需求

管理层需 2025-04、各二级品类按周的下单数 `order_cnt`，无筛选，用于细分品类的成交观察。

- 时间范围：2025-04
- 维度：二级品类
- 核心指标：下单数 `order_cnt`

## 结论

`dws_trade_cate2_order_1w` 对口，为二级品类粒度的按周下单汇总表。

- 取数字段：`stat_date`、`category2_id`、`category2_name`、`order_cnt`、`item_qty`、`paid_amount`
- 时间范围：`stat_date` 限定 2025-04
- 口径：`order_cnt` 不可跨行相加，多周或多品类累加会重复计数
- 建议：如需累计规模，改用可加的 `item_qty`、`paid_amount`
- 交付：已交付，附 SQL 供需求方自助复用
