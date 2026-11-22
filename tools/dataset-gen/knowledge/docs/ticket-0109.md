# 取数需求单 REQ-2025-0109

## 需求

物流部需 2025-03、收货省份为上海市的按日已支付明细金额 `paid_amount`（元），筛选只看上海市，用于该市成交金额的专项观察。

- 时间范围：2025-03
- 维度：收货省份（只看上海市）
- 核心指标：已支付明细金额 `paid_amount`

## 结论

`dws_trade_prov_order_1d` 对口，为收货省份粒度的按日下单汇总表。

- 取数字段：`stat_date`、`receiver_province`、`order_cnt`、`item_qty`、`paid_amount`
- 过滤：`receiver_province` 等于上海市，`stat_date` 限定 2025-03
- 可加：`paid_amount`、`item_qty` 可跨行相加，上海市按日累加成月度合计成立
- 注意：`order_cnt` 不可相加
- 交付：已交付，附 SQL 供需求方自助复用
