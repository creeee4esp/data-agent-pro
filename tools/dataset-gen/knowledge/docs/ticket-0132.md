# 取数需求单 REQ-2025-0132

## 需求

管理层需 2025-04、各仓库按月的已签收单数 `signed_cnt`，无筛选，用于仓库履约的月度对比。

- 时间范围：2025-04
- 维度：仓库
- 核心指标：已签收单数 `signed_cnt`

## 结论

`dws_lgs_warehouse_ship_1m` 对口，为仓库粒度的按月发货汇总表。

- 取数字段：`stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`
- 时间范围：`stat_date` 限定 2025-04
- 可加：`signed_cnt`、`ship_cnt` 可跨行相加，多仓汇总成全局签收量成立
- 注意：`avg_ship_hours` 为平均值，不可相加，跨仓须按单量加权
- 交付：已交付，附 SQL 供需求方自助复用
