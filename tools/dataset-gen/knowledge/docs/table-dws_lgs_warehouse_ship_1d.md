# 表说明：dws_lgs_warehouse_ship_1d（DWS-仓库粒度发货汇总（日））

## 概述

`dws_lgs_warehouse_ship_1d` 为仓库粒度的每日发货汇总，归属 DWS 层，表注释为 DWS-仓库粒度发货汇总（日），当前共 2,137 行。其粒度为统计日期、仓库及其所属大区，由物流明细 `dwd_lgs_shipment_di` 按日和仓库聚合而成，用于仓库发货量与时效的每日监控。使用时应注意，该表维度为发货仓库，与按收货省份的物流汇总口径不同。

## 字段

- `stat_date` 为 date 类型的统计日期，是日粒度的时间轴。
- `warehouse_id` 为 int 类型的仓库ID，是最细的拆分维度。
- `warehouse_region` 为 varchar(10) 类型的仓库大区，为仓库所属大区，可用于上卷聚合。
- `ship_cnt` 为 int 类型的发货单数，可按日期区间累加。
- `signed_cnt` 为 int 类型的已签收单数，可累加，作签收率分子。
- `avg_ship_hours` 为 decimal(10,2) 类型的平均发货时长，单位小时，为当日均值，不可跨日直接再平均。

## 上下游

`dws_lgs_warehouse_ship_1d` 的唯一上游为 `dwd_lgs_shipment_di`，加工时按发货日期、仓库 ID 与仓库大区分组，统计发货单数、已签收单数与平均发货时长。该表无下游表，供分析按需使用。

## 使用注意

`dws_lgs_warehouse_ship_1d` 适用于仓库每日发货量、签收情况与发货时效的监控及仓库间对比，签收率以 `signed_cnt / ship_cnt` 计算，区间统计须分别累加后相除。该表不含配送时长，亦不支持按承运商或收货省份拆分。本家族存在备份表 `dws_lgs_warehouse_ship_1d_bak`、带日期备份 `dws_lgs_warehouse_ship_1d_bak_20250423` 等与旧版本表 `dws_lgs_warehouse_ship_1d_old`，分别为时点副本与历史口径残留，均不得用于统计，口径一律以 `dws_lgs_warehouse_ship_1d` 为准。
