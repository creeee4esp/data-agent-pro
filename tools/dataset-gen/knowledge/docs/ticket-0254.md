# 取数需求单 REQ-2025-0254

## 需求

会员中心 2025-10-28 提的，想要 2025-09 的发货单数（`ship_cnt`），按收货省份粒度、周度发货口径拉一份，没有额外筛选。单号 REQ-2025-0254，交易数据组接的。

## 结论

`dws_lgs_prov_ship_1w`（DWS-收货省份粒度发货汇总（周））这张表够用，字段用 `stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，日期 `stat_date` 取 2025-09 内的各周。

加和这块：`ship_cnt` 和 `signed_cnt` 都可跨行相加，各省份、各周的 `ship_cnt` 加起来算发货总量没问题。`avg_ship_hours` 是平均值，不能跨行相加，要看整体时长得按 `ship_cnt` 加权，别直接求和。

这次已交付一次性取数结果。
