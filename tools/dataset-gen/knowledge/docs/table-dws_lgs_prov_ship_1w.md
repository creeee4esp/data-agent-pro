# 表说明：dws_lgs_prov_ship_1w（DWS-收货省份粒度发货汇总（周））

## 概述

`dws_lgs_prov_ship_1w` 是收货省份维度的周度发货汇总，属于 DWS 层，表注释为 DWS-收货省份粒度发货汇总（周），当前 848 行。它的粒度是统计周加收货省份，由物流明细 `dwd_lgs_shipment_di` 按自然周聚合，周以周一为起点。看各省份按周的发货量和签收波动，它比按日表更平滑、比按月表更细。维度是收货省份，不是发货仓大区，这点要记牢。

## 字段

- `stat_date` 是 date 的统计周，用该周的周一日期表示。
- `receiver_province` 是 varchar(20) 的收货省份，按收货地划分。
- `ship_cnt` 是 int 的发货单数，可跨周相加。
- `signed_cnt` 是 int 的已签收单数，可跨周相加，作签收率分子。
- `avg_ship_hours` 是 decimal(10,2) 的平均发货时长，单位小时，是周内均值，跨周别直接再平均。

## 上下游

`dws_lgs_prov_ship_1w` 的唯一上游是 `dwd_lgs_shipment_di`，加工时把发货日期对齐到所在周的周一并按收货省份分组汇总。它没有下游表，供分析按需使用。

## 使用注意

`dws_lgs_prov_ship_1w` 适合做收货省份的周度发货量和签收率趋势分析。签收率用 `SUM(signed_cnt) / SUM(ship_cnt)`，别平均周值。它不含配送时长，不能按承运商或仓库拆分。本家族有备份表 `dws_lgs_prov_ship_1w_bak`、带日期备份 `dws_lgs_prov_ship_1w_bak_20250310` 和旧版本表 `dws_lgs_prov_ship_1w_v2`，分别是时点副本和历史残留，不保证完整，统计一律用 `dws_lgs_prov_ship_1w`。
