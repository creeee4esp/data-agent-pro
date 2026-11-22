# 表说明：ods_lgs_shipment_di（ODS-物流发货单表）

## 概述

`ods_lgs_shipment_di` 是 ODS 层的物流发货单贴源表，原样承接业务侧的发货与签收记录，当前 17,059 行，一行一条物流单。相比业务表，它多了 `etl_time` 和 `dt` 两个技术字段。它是物流链路往上加工的起点，发货量、签收率、承运商和仓库维度的指标都从这里往下走。

## 字段

- `ship_id`（bigint）：物流单 ID，发货单的唯一标识。
- `ord_no`（bigint）：订单号，即 `trade_order.order_id`，要和订单关联就靠它。
- `warehouse_id`（int）：发货仓库 ID，想带出仓库信息用它关联仓库维度。
- `carrier`（varchar(20)）：承运商，sf-顺丰、jd-京东、yto-圆通、zto-中通，按承运商拆分用这些枚举值。
- `ship_time`（datetime）：发货时间，按发货日期统计用它。
- `sign_time`（datetime）：签收时间，未签收为空，算签收率或时效前要先排除空值。
- `etl_time`（datetime）：ETL 同步时间，技术字段，不是业务时间，别拿它做业务口径。
- `dt`（date）：分区日期，查询时用它裁剪分区。

## 上下游

`ods_lgs_shipment_di` 的上游是 `lgs_shipment`，通过 ETL 同步落到 ODS 层。它的下游是 `dwd_lgs_shipment_di`，由后者承接做明细层加工。

## 使用注意

`ods_lgs_shipment_di` 适合做发货量、签收率、承运商占比和仓库维度的物流分析，查询时建议带 `dt` 做分区过滤。它不适合承载订单金额或商品属性，那些要去交易、商品相关的表取。库里同家族还有噪声表：备份表 `ods_lgs_shipment_di_bak`、旧版本 `ods_lgs_shipment_di_v2`、测试表 `test_ods_lgs_shipment_di`。它们分别是历史快照、早期口径和调试数据，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_lgs_shipment_di`。
