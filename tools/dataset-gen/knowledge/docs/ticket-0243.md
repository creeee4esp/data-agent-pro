# 取数需求单 REQ-2025-0243

## 需求

管理层 2025-10-20 提的，想要 2025-02 至 2025-04 的发货单数（`ship_cnt`），按仓库粒度、周度发货口径拉，没别的筛选。单号 REQ-2025-0243，用户数据组接的。

## 结论

`dws_lgs_warehouse_ship_1w`（DWS-仓库粒度发货汇总（周））这张表能满足，字段用 `stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，日期 `stat_date` 卡在 2025-02 到 2025-04 的各周。

加和这块：`ship_cnt` 和 `signed_cnt` 都可跨行相加，各仓、各周的 `ship_cnt` 加起来算发货总量没问题。`avg_ship_hours` 是平均值，不能跨行相加，要看整体时长得按 `ship_cnt` 加权，别直接求和。

这次已交付，并沉淀为固定报表。
