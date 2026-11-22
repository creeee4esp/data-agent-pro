# 取数需求单 REQ-2025-0251

## 需求

商品部于 2025-10-27 提出取数，需要 2025-06 至 2025-07 的已签收单数（`signed_cnt`），口径为收货省份粒度发货日汇总，无筛选条件。编号 REQ-2025-0251，处理人商品数据组。

## 结论

`dws_lgs_prov_ship_1d`（DWS-收货省份粒度发货汇总（日））为本次用表，取 `stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，报告期 `stat_date` 覆盖 2025-06 至 2025-07。

加和口径：`signed_cnt`、`ship_cnt` 可跨行相加，汇总签收与发货单量用这两个字段；`avg_ship_hours` 为平均值，不可跨行相加，如需整体时效应按 `ship_cnt` 加权。

本次已交付，并沉淀为固定报表。
