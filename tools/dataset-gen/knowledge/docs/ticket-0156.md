# 取数需求单 REQ-2025-0156

## 需求

市场部于 2025-07-04 提单，核心诉求如下：

- 对象：仓库粒度的已签收单数 `signed_cnt`
- 周期：按日汇总
- 报告期：2025-04 至 2025-05
- 筛选：无

## 结论

`dws_lgs_warehouse_ship_1d` 可满足本需求，要点如下：

- 取数字段：`stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，按 `stat_date` 限定 2025-04 至 2025-05
- 口径提示：`ship_cnt`、`signed_cnt` 可跨行相加，按仓库或按期汇总均成立
- 注意：`avg_ship_hours` 为平均值，不能直接累加，跨期整体时长须加权重算
- 交付：已交付一次性取数结果
