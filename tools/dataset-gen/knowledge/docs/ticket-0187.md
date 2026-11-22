# 取数需求单 REQ-2025-0187

## 需求

物流部于 2025-08-09 提单，要点如下：

- 对象：收货省份粒度的下单数 `order_cnt`
- 周期：按周汇总
- 报告期：2025-08
- 筛选：无

## 结论

`dws_trade_prov_order_1w` 可满足本需求，要点如下：

- 取数字段：`stat_date`、`receiver_province`、`order_cnt`、`item_qty`、`paid_amount`，按 `stat_date` 限定 2025-08
- 关键口径：`order_cnt` 为去重下单数，不能跨行累加，各省相加不等于全国总下单数
- 可加和项：`item_qty` 与 `paid_amount` 可直接相加
- 交付：已交付，附 SQL 供物流部自助复用
