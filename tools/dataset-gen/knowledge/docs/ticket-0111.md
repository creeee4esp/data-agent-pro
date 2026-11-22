# 取数需求单 REQ-2025-0111

## 需求

运营部需 2025-03 至 2025-05、各仓库按月的平均发货时长 `avg_ship_hours`（小时），无筛选，用于仓库时效的季度监控。

- 时间范围：2025-03 至 2025-05
- 维度：仓库
- 核心指标：平均发货时长 `avg_ship_hours`

## 结论

`dws_lgs_warehouse_ship_1m` 对口，为仓库粒度的按月发货汇总表。

- 取数字段：`stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`
- 时间范围：`stat_date` 限定 2025-03 至 2025-05
- 关键口径：`avg_ship_hours` 不可跨行相加，跨仓或跨月不能直接平均，须用 `ship_cnt` 加权
- 可加：`ship_cnt`、`signed_cnt` 可跨行相加
- 交付：已交付，并沉淀为固定报表
