# 取数需求单 REQ-2025-0252

## 需求

会员中心于 2025-10-27 提出取数，需要 2025-07 的发货单数（`ship_cnt`），口径为仓库粒度发货月汇总，无筛选条件。编号 REQ-2025-0252，处理人交易数据组。

## 结论

`dws_lgs_warehouse_ship_1m`（DWS-仓库粒度发货汇总（月））为本次用表，取 `stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，报告期 `stat_date` 取 2025-07。

加和口径：`ship_cnt`、`signed_cnt` 可跨行相加，各仓库汇总得到全站发货与签收单量；`avg_ship_hours` 不可跨行相加，如需整体时效应按 `ship_cnt` 加权。

本次已交付，附 SQL 供需求方自助复用。
