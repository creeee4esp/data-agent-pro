# 表说明：dwd_lgs_shipment_di（DWD-物流明细）

## 概述

`dwd_lgs_shipment_di` 是物流发货的明细宽表，属于 DWD 层，表注释为 DWD-物流明细，当前 17,059 行，一条物流单一行。它由贴源物流表 `ods_lgs_shipment_di` 关联订单表 `ods_trade_order_di` 和仓库表 `ods_lgs_warehouse_di` 加工，补齐了仓库大区、承运商、收货省份和两段时效。要算时效或签收率，基本都从它出发，例如 `SELECT carrier, AVG(ship_hours) FROM dwd_lgs_shipment_di GROUP BY carrier`。

## 字段

- `ship_id` 是 bigint 的物流单ID，是本表主键。
- `order_id` 是 bigint 的订单ID，关联回对应订单。
- `warehouse_id` 是 int 的仓库ID。
- `warehouse_region` 是 varchar(10) 的仓库大区，按发货仓所在大区聚合时用它。
- `carrier` 是 varchar(20) 的承运商。
- `receiver_province` 是 varchar(20) 的收货省份。
- `ship_hours` 是 int 的支付到发货的小时数，衡量发货速度。
- `deliver_hours` 是 int 的发货到签收的小时数，未签收为空，算平均配送时长要用 `WHERE deliver_hours IS NOT NULL` 过滤，否则样本不对。
- `is_signed` 是 tinyint 的是否已签收，签收率用它求和除以单量。
- `ship_date` 是 date 的发货日期，是业务时间。
- `dt` 是 date 的分区日期，用于分区裁剪，别当发货时间用。

## 上下游

`dwd_lgs_shipment_di` 的上游是 `ods_lgs_shipment_di`、`ods_trade_order_di` 和 `ods_lgs_warehouse_di`。下游很多，包括 `ads_lgs_delivery_monthly`，以及承运商、收货省份、仓库三个粒度的日/周/月汇总，如 `dws_lgs_carrier_ship_1d`、`dws_lgs_prov_ship_1m`、`dws_lgs_warehouse_ship_1w` 等。

## 使用注意

`dwd_lgs_shipment_di` 适合做物流时效、签收率的灵活分析，比如按承运商看发货时长 `SELECT carrier, ROUND(AVG(ship_hours),2) FROM dwd_lgs_shipment_di GROUP BY carrier`，或按仓库大区看签收率。固定口径的时效统计建议直接用 DWS/ADS 汇总表。两个坑：算配送时长要先过滤 `deliver_hours` 非空；区分 `ship_date` 与 `dt`。本家族有备份表 `dwd_lgs_shipment_di_bak` 和带日期备份 `dwd_lgs_shipment_di_bak_20250315` 等，都是时点副本，不保证完整，统计一律用 `dwd_lgs_shipment_di`。
