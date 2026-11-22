# 取数需求单 REQ-2025-0223

## 需求

运营部在 2025-09-20 提出取数，关注 2025-08 的平均发货时长（`avg_ship_hours`），希望按仓库粒度、月度口径了解各仓的发货效率，无筛选条件。本单编号 REQ-2025-0223，处理人用户数据组。运营部想借此横向比较各仓当月的发货快慢。

## 结论

`dws_lgs_warehouse_ship_1m` 即 DWS-仓库粒度发货汇总（月）正对应这一诉求，取 `stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，报告期 `stat_date` 取 2025-08。

分析时要提醒运营部：`avg_ship_hours` 是平均值，不可跨行相加，不能把各仓的 `avg_ship_hours` 相加或简单取算术平均来代表整体时长，如需全局平均应以 `ship_cnt` 为权重加权。`ship_cnt`、`signed_cnt` 可跨行相加，用来统计发货单量与签收单量。

本次已交付一次性取数结果。
