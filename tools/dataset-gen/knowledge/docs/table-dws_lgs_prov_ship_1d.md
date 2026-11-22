# 表说明：dws_lgs_prov_ship_1d（DWS-收货省份粒度发货汇总（日））

## 概述

`dws_lgs_prov_ship_1d` 是收货省份维度的每日发货汇总，属于 DWS 层，表注释为 DWS-收货省份粒度发货汇总（日），当前 5,088 行。粒度是统计日期加收货省份，由物流明细 `dwd_lgs_shipment_di` 按日和收货省份聚合而来。要先点一处易错点：这里的维度是收货省份，不是发货仓所在大区，别把它当成仓库维度看。

## 字段

- `stat_date` 是 date 的统计日期，是日粒度的时间轴。
- `receiver_province` 是 varchar(20) 的收货省份，按收货地划分，是除日期外的拆分维度。
- `ship_cnt` 是 int 的发货单数，可按日期区间相加。
- `signed_cnt` 是 int 的已签收单数，可相加，作签收率分子。
- `avg_ship_hours` 是 decimal(10,2) 的平均发货时长，单位小时，是当日均值，跨日不要直接再平均。

## 上下游

`dws_lgs_prov_ship_1d` 的唯一上游是 `dwd_lgs_shipment_di`，加工时按发货日期和收货省份分组，统计发货单数、已签收单数和平均发货时长。它没有下游表，供分析按需使用。

## 使用注意

`dws_lgs_prov_ship_1d` 适合做收货省份的每日发货量和签收情况分析。签收率用 `signed_cnt / ship_cnt`，算区间要先分别累加再相除。它不含配送时长，也不能按承运商或仓库拆分，别误用 `receiver_province` 当发货仓维度。本家族有带日期备份 `dws_lgs_prov_ship_1d_bak_20250901` 等和旧版本表 `dws_lgs_prov_ship_1d_v1`，分别是时点副本和历史口径残留，不保证完整，统计一律用 `dws_lgs_prov_ship_1d`。
