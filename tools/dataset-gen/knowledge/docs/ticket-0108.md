# 取数需求单 REQ-2025-0108

## 需求

财务部需 2025-02 至 2025-03、各二级品类按周的下单数 `order_cnt`，无筛选，用于细分品类成交的财务跟踪。

- 时间范围：2025-02 至 2025-03
- 维度：二级品类
- 核心指标：下单数 `order_cnt`

## 结论

`dws_trade_cate2_order_1w` 对口，为二级品类粒度的按周下单汇总表。

- 取数字段：`stat_date`、`category2_id`、`category2_name`、`order_cnt`、`item_qty`、`paid_amount`
- 时间范围：`stat_date` 限定 2025-02 至 2025-03
- 口径：`order_cnt` 不可跨行相加，跨周或多品类累加会重复计数
- 建议：如需累计规模，改用可加的 `item_qty`、`paid_amount`
- 交付：已交付，并沉淀为固定报表
