# 取数需求单 REQ-2025-0176

## 需求

物流部 2025-07-27 提单：仓库粒度发货单数 `ship_cnt`，按日汇总，区间 2025-04 至 2025-05，无筛选。

## 结论

`dws_lgs_warehouse_ship_1d` 命中需求。取数字段：`stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`；按 `stat_date` 卡 2025-04 至 2025-05。口径：`ship_cnt`、`signed_cnt` 可跨行相加；`avg_ship_hours` 是平均值，不能直接累加，整体时长须加权重算。本次已交付一次性取数结果。
