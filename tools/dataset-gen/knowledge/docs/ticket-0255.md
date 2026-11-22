# 取数需求单 REQ-2025-0255

## 需求

物流部 2025-10-29 提的，想要 2025-06 至 2025-07 的平均发货时长（`avg_ship_hours`），按收货省份粒度、月度发货口径拉，没别的筛选。单号 REQ-2025-0255，数据平台组接的。

## 结论

`dws_lgs_prov_ship_1m`（DWS-收货省份粒度发货汇总（月））这张表能满足，字段用 `stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，日期 `stat_date` 取 2025-06 和 2025-07。

有个地方得提醒：`avg_ship_hours` 是平均值，不能跨行相加，别把各省份的 `avg_ship_hours` 加起来或简单取平均当整体时长，要算全局平均得按 `ship_cnt` 加权。`ship_cnt` 和 `signed_cnt` 可跨行相加，用来统计发货量和签收量。

这次已交付，附 SQL 供需求方自助复用。
