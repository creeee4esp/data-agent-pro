# 取数需求单 REQ-2025-0097

## 需求

商品部需 2025-03、按收货省份看每日购买件数 `item_qty`，无筛选，用于各省销量分布速览。

- 时间范围：2025-03
- 维度：收货省份
- 核心指标：购买件数 `item_qty`

## 结论

`dws_trade_prov_order_1d` 对口，为收货省份粒度的按日下单汇总表。

- 取数字段：`stat_date`、`receiver_province`、`order_cnt`、`item_qty`、`paid_amount`
- 时间范围：`stat_date` 限定 2025-03
- 可加：`item_qty`、`paid_amount` 可跨行相加，跨日或跨省汇总均可
- 注意：`order_cnt` 不可相加，跨行累加会重复计数
- 交付：已交付一次性取数结果
