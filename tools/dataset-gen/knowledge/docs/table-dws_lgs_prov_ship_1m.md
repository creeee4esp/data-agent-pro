# 表说明：dws_lgs_prov_ship_1m（DWS-收货省份粒度发货汇总（月））

## 概述

`dws_lgs_prov_ship_1m` 是收货省份维度的月度发货汇总，属于 DWS 层，表注释为 DWS-收货省份粒度发货汇总（月），当前 205 行。它的粒度是统计月加收货省份，由物流明细 `dwd_lgs_shipment_di` 按月聚合而来。想看各省份的月度发货量和签收情况，用它最合适。要跟新同事说清楚，`stat_date` 用月初日期表示一个月，不是月份字符串，且维度是收货省份不是发货仓大区。

## 字段

- `stat_date` 是 date 的统计月，用月初日期表示该月，如 2025-06-01。
- `receiver_province` 是 varchar(20) 的收货省份，按收货地划分。
- `ship_cnt` 是 int 的发货单数，可跨月相加。
- `signed_cnt` 是 int 的已签收单数，可跨月相加，作签收率分子。
- `avg_ship_hours` 是 decimal(10,2) 的平均发货时长，单位小时，是月内均值，跨月别直接再平均。

## 上下游

`dws_lgs_prov_ship_1m` 的唯一上游是 `dwd_lgs_shipment_di`，加工时把发货日期归到月初并按收货省份分组汇总。它没有下游表，供分析按需使用。

## 使用注意

`dws_lgs_prov_ship_1m` 适合做收货省份的月度发货量和签收率对比。签收率用 `SUM(signed_cnt) / SUM(ship_cnt)`，别平均日/月值。它不含配送时长，不能按承运商或仓库拆分。本家族有备份表 `dws_lgs_prov_ship_1m_bak`、带日期备份 `dws_lgs_prov_ship_1m_bak_20250128` 等和旧版本表 `dws_lgs_prov_ship_1m_v2`，分别是时点副本和历史残留，不保证完整，统计一律用 `dws_lgs_prov_ship_1m`。
