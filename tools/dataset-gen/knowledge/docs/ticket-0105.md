# 取数需求单 REQ-2025-0105

## 需求

管理层现提出取数需求，拟获取 2025-01 至 2025-02 期间各仓库按月的平均发货时长 `avg_ship_hours`（小时），不附加筛选条件，用于评估各仓的发货时效。

## 结论

经评估，`dws_lgs_warehouse_ship_1m` 系仓库粒度的按月发货汇总表，可满足本次要求。所取字段为 `stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，时间范围依 `stat_date` 限定于 2025-01 至 2025-02。

需郑重提示：平均发货时长 `avg_ship_hours` 为平均值，不可跨行相加，亦不可将多仓或多月的数值直接取简单平均。如需跨仓或整体的平均时效，应以发货单数 `ship_cnt` 作权重重新加权计算。同表的 `ship_cnt`、`signed_cnt` 可跨行相加。

本需求已按一次性取数交付管理层。
