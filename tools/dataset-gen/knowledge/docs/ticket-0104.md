# 取数需求单 REQ-2025-0104

## 需求

运营部需 2025-02、按收货省份看每周下单数 `order_cnt`，无筛选，用于各省成交的周度跟踪。

- 时间范围：2025-02
- 维度：收货省份
- 核心指标：下单数 `order_cnt`

## 结论

`dws_trade_prov_order_1w` 对口，为收货省份粒度的按周下单汇总表。

- 取数字段：`stat_date`、`receiver_province`、`order_cnt`、`item_qty`、`paid_amount`
- 时间范围：`stat_date` 限定 2025-02
- 口径：`order_cnt` 不可跨行相加，跨周或跨省累加会重复计数
- 建议：如需累计规模，改用可加的 `item_qty`、`paid_amount`
- 交付：已交付，附 SQL 供需求方自助复用
