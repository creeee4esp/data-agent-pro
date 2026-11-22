# 取数需求单 REQ-2025-0278

## 需求

财务部于 2025-11-26 提出取数，要点如下：

- 指标：平均发货时长（`avg_ship_hours`）
- 口径：收货省份粒度发货月汇总
- 时间范围：2025-05 至 2025-07
- 筛选条件：无
- 单据编号：REQ-2025-0278，处理人流量数据组

## 结论

`dws_lgs_prov_ship_1m`（DWS-收货省份粒度发货汇总（月））为本次用表，要点如下：

- 取用字段：`stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`
- 报告期：`stat_date` 覆盖 2025-05 至 2025-07
- 关键提醒：`avg_ship_hours` 为平均值，不可跨行相加，求整体时效须以 `ship_cnt` 加权，不能简单求和或取算术平均
- 可加字段：`ship_cnt`、`signed_cnt` 可跨行相加
- 交付情况：已交付，并沉淀为固定报表
