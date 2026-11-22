# 取数需求单 REQ-2025-0230

## 需求

商品部 2025-10-02 提的，要 2025-01 至 2025-03 的已签收单数（`signed_cnt`），按收货省份粒度、日度发货口径拉，没别的筛选。单号 REQ-2025-0230，交易数据组接的。

## 结论

`dws_lgs_prov_ship_1d`（DWS-收货省份粒度发货汇总（日））就能满足，字段用 `stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，日期 `stat_date` 卡在 2025-01 到 2025-03。

加和这块：`signed_cnt` 和 `ship_cnt` 都可跨行相加，各省份、各天的 `signed_cnt` 加起来算签收总量没问题。但 `avg_ship_hours` 是平均值，不能跨行相加，要看整体时长得按 `ship_cnt` 加权，别直接求和或取平均。

这次已交付一次性取数结果。
