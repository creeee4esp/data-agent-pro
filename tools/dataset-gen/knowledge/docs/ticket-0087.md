# 取数需求单 REQ-2025-0087

## 需求

管理层需 2025-03 至 2025-04、各仓库按月的发货单数 `ship_cnt`，无筛选，用于两月仓库产能对比。

## 结论

`dws_lgs_warehouse_ship_1m` 对口，为仓库粒度的按月发货汇总表。

- 字段：`stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`
- 时间范围：`stat_date` 限定 2025-03 至 2025-04
- 可加：`ship_cnt`、`signed_cnt` 可跨行相加
- 提示：`avg_ship_hours` 为平均值，不可相加，跨仓汇总需按单量加权
- 交付：已交付一次性取数结果
