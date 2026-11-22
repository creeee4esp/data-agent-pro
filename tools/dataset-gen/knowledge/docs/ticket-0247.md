# 取数需求单 REQ-2025-0247

## 需求

商品部于 2025-10-25 正式提出取数申请，请求提供 2025-02 至 2025-04 的已签收单数（`signed_cnt`），统计口径为仓库粒度发货日度汇总，不设筛选条件。本单编号 REQ-2025-0247，交由物流数据组承办。

## 结论

`dws_lgs_warehouse_ship_1d` 即 DWS-仓库粒度发货汇总（日）为本次取数之依据表，取用 `stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，报告期 `stat_date` 覆盖 2025-02 至 2025-04。

须予说明：`signed_cnt` 与 `ship_cnt` 可跨行相加，将各仓库、各日的 `signed_cnt` 累加以得签收单量合计系属合理。惟 `avg_ship_hours` 为平均值，不可跨行相加，如需整体发货时效，应以 `ship_cnt` 为权重加权计算，不得简单求和或取算术平均。

本次已交付一次性取数结果。
