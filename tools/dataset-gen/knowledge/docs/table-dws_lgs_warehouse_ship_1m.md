# 表说明：dws_lgs_warehouse_ship_1m（DWS-仓库粒度发货汇总（月））

## 概述

`dws_lgs_warehouse_ship_1m` 是仓库维度的月度发货汇总，属于 DWS 层，表注释为 DWS-仓库粒度发货汇总（月），当前 77 行。它的粒度是统计月、仓库及其所属大区，由物流明细 `dwd_lgs_shipment_di` 按月聚合而来。想看各仓库的月度发货量和时效，用它比查明细省事。要说清楚两点：`stat_date` 用月初日期表示一个月，维度是发货仓库而非收货省份。

## 字段

- `stat_date` 是 date 的统计月，用月初日期表示该月，如 2025-06-01。
- `warehouse_id` 是 int 的仓库ID，是最细的拆分维度。
- `warehouse_region` 是 varchar(10) 的仓库大区，是仓库所属大区，可用来上卷。
- `ship_cnt` 是 int 的发货单数，可跨月相加。
- `signed_cnt` 是 int 的已签收单数，可跨月相加，作签收率分子。
- `avg_ship_hours` 是 decimal(10,2) 的平均发货时长，单位小时，是月内均值，跨月别直接再平均。

## 上下游

`dws_lgs_warehouse_ship_1m` 的唯一上游是 `dwd_lgs_shipment_di`，加工时把发货日期归到月初，按仓库 ID 和仓库大区分组汇总。它没有下游表，供分析按需使用。

## 使用注意

`dws_lgs_warehouse_ship_1m` 适合做仓库的月度发货量和签收率对比，也可按 `warehouse_region` 上卷到大区。签收率用 `SUM(signed_cnt) / SUM(ship_cnt)`，别平均。它不含配送时长，不能按承运商或收货省份拆分。本家族有备份表 `dws_lgs_warehouse_ship_1m_bak` 和带日期备份 `dws_lgs_warehouse_ship_1m_bak_20250317` 等，都是时点副本，不保证完整，统计一律用 `dws_lgs_warehouse_ship_1m`。
