# 表说明：dws_lgs_carrier_ship_1d（DWS-承运商粒度发货汇总（日））

## 概述

`dws_lgs_carrier_ship_1d` 是承运商维度的每日发货汇总，属于 DWS 层，表注释为 DWS-承运商粒度发货汇总（日），当前 1,468 行。它的粒度是统计日期加承运商，由物流明细 `dwd_lgs_shipment_di` 按日和承运商聚合而来。想看各家承运商每天发多少、签收多少、发货多快，用它比查明细省事。

## 字段

- `stat_date` 是 date 的统计日期，是日粒度的时间轴。
- `carrier` 是 varchar(20) 的承运商，是除日期外的拆分维度。
- `ship_cnt` 是 int 的发货单数，可按日期区间相加。
- `signed_cnt` 是 int 的已签收单数，可相加，算签收率时作分子。
- `avg_ship_hours` 是 decimal(10,2) 的平均发货时长，单位小时，是当日均值，跨日不要把多天的值直接再平均。

## 上下游

`dws_lgs_carrier_ship_1d` 的唯一上游是 `dwd_lgs_shipment_di`，加工时按发货日期和承运商分组，统计发货单数、已签收单数和平均发货时长。它没有下游表，供分析按需使用。

## 使用注意

`dws_lgs_carrier_ship_1d` 适合做承运商的每日发货量、签收情况和发货时效对比。签收率用 `signed_cnt / ship_cnt`，算区间签收率要分别累加两者再相除；算区间平均发货时长也不要直接平均日值。它不含配送时长，也不能按省份或仓库拆分。本家族有带日期备份 `dws_lgs_carrier_ship_1d_bak_20250115` 等和旧版本表 `dws_lgs_carrier_ship_1d_v1`，分别是时点副本和历史口径残留，不保证完整，统计一律用 `dws_lgs_carrier_ship_1d`。
