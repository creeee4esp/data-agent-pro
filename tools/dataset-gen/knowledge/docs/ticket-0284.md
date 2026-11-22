# 取数需求单 REQ-2025-0284

## 需求

商品部在 2025-12-07 提出取数，关注 2025-12 的平均发货时长（`avg_ship_hours`），希望按收货省份粒度、月度发货口径了解各省份当月的发货效率，不设筛选条件。本单编号 REQ-2025-0284，处理人商品数据组。商品部想据此横向比较各省当月的发货快慢。

## 结论

`dws_lgs_prov_ship_1m` 即 DWS-收货省份粒度发货汇总（月）对应本次取数，取 `stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，报告期 `stat_date` 取 2025-12。

分析时要强调：`avg_ship_hours` 是平均值，不可跨行相加，既不能把各省份的 `avg_ship_hours` 相加，也不能简单取算术平均来代表整体时长，若要全国整体平均应以 `ship_cnt` 为权重加权。`ship_cnt`、`signed_cnt` 可跨行相加，用于统计发货与签收单量。

本次已交付一次性取数结果。
