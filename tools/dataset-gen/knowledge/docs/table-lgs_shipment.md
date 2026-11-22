# 表说明：lgs_shipment（物流发货单表）

## 概述

`lgs_shipment` 是 CORE 层的物流发货单表，记录订单的发货与签收信息。

- 粒度：一条物流单一行。
- 当前规模：17,059 行。
- 适用场景：发货量、签收情况、承运商与仓库维度的物流分析。

## 字段

- `ship_id`（bigint）：物流单 ID，发货单的唯一标识。
- `ord_no`（bigint）：订单号，即 `trade_order.order_id`，与订单关联用它。
- `warehouse_id`（int）：发货仓库 ID，指向 `lgs_warehouse.warehouse_id`。
- `carrier`（varchar(20)）：承运商，取值 sf-顺丰、jd-京东、yto-圆通、zto-中通，按承运商统计用这些枚举值。
- `ship_time`（datetime）：发货时间，按发货日期统计用它。
- `sign_time`（datetime）：签收时间，未签收为空，算签收率或时效时要先排除空值。

## 上下游

`lgs_shipment` 的上游是业务系统（在线数据库）。

- 下游：ODS 层的 `ods_lgs_shipment_di`，由它承接再往上层加工。
- 关联：`lgs_shipment.warehouse_id` 指向 `lgs_warehouse.warehouse_id`，可带出仓库名称与大区。

## 使用注意

`lgs_shipment` 适合做发货量、签收率、承运商占比和仓库维度的物流统计。

- 不适合：订单金额、商品属性类分析，应取交易或商品相关表。
- 噪声表：备份表 `lgs_shipment_bak`、带日期备份如 `lgs_shipment_bak_20250301`、日分表（`lgs_shipment_20250101 ～ lgs_shipment_20251231`，如 `lgs_shipment_20250101`）、月分表（`lgs_shipment_202301 ～ lgs_shipment_202512`，如 `lgs_shipment_202301`）。
- 为什么不能用：备份是历史快照，日分表与月分表是按期拆分的存档，口径与完整性都不保证。
- 正式取数只认 `lgs_shipment`。
