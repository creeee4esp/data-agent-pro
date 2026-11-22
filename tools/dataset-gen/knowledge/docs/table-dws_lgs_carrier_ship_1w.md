# 表说明：dws_lgs_carrier_ship_1w（DWS-承运商粒度发货汇总（周））

## 概述

`dws_lgs_carrier_ship_1w` 是承运商维度的周度发货汇总，属于 DWS 层，表注释为 DWS-承运商粒度发货汇总（周），当前 212 行。粒度是统计周加承运商，由物流明细 `dwd_lgs_shipment_di` 按自然周聚合，周以周一为起点。看某家承运商按周的发货量和签收率，直接用它，比如 `SELECT stat_date, ship_cnt, signed_cnt FROM dws_lgs_carrier_ship_1w WHERE carrier = '某承运商' ORDER BY stat_date`。

## 字段

- `stat_date` 是 date 的统计周，用该周的周一日期表示。
- `carrier` 是 varchar(20) 的承运商，是除周外的拆分维度。
- `ship_cnt` 是 int 的发货单数，可跨周相加。
- `signed_cnt` 是 int 的已签收单数，可跨周相加，作签收率分子。
- `avg_ship_hours` 是 decimal(10,2) 的平均发货时长，单位小时，是周内均值，跨周别直接再平均。

## 上下游

`dws_lgs_carrier_ship_1w` 的唯一上游是 `dwd_lgs_shipment_di`，加工时把发货日期对齐到所在周的周一并按承运商分组汇总。它没有下游表，供分析按需使用。

## 使用注意

`dws_lgs_carrier_ship_1w` 适合做承运商的周度发货量和签收率趋势分析。签收率用 `SUM(signed_cnt) / SUM(ship_cnt)`，别用平均；算区间平均发货时长也不要直接平均周值。它不含配送时长，也不能按省份或仓库拆分。本家族有备份表 `dws_lgs_carrier_ship_1w_bak`、带日期备份 `dws_lgs_carrier_ship_1w_bak_20250106` 等和测试表 `test_dws_lgs_carrier_ship_1w`，分别是时点副本和调试产物，不能用于统计，口径一律用 `dws_lgs_carrier_ship_1w`。
