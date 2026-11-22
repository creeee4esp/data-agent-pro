# 表说明：ods_lgs_warehouse_di（ODS-仓库表）

## 概述

`ods_lgs_warehouse_di` 为 ODS 层的仓库贴源表，原样承接业务侧仓库基础信息，当前共 6 行。该表以仓库为粒度，一行描述一个仓库，相比业务表增加了 `etl_time` 与 `dt` 两个技术字段。其数据量极小且稳定，主要作为维度表向下游供数，用于为发货数据补齐仓库的大区与省份归属。

## 字段

- `warehouse_id`（int）：仓库 ID，作为仓库的唯一标识，关联时以该字段为准。
- `warehouse_name`（varchar(50)）：仓库名称，用于展示。
- `region`（varchar(10)）：所属大区，取值为华东、华南、华北、华中、西南、西北，按大区汇总时应使用对应枚举值。
- `province`（varchar(20)）：仓库所在省份。
- `etl_time`（datetime）：ETL 同步时间，为技术字段，不代表业务发生时间。
- `dt`（date）：分区日期，用于分区裁剪。

## 上下游

`ods_lgs_warehouse_di` 的数据来自上游的 `lgs_warehouse`，经 ETL 同步进入 ODS 层。其下游为 `dwd_lgs_shipment_di`，由后者在明细层关联并带出仓库维度信息。

## 使用注意

`ods_lgs_warehouse_di` 适合作为维度表为发货数据补齐仓库名称、大区及省份，查询时建议带 `dt` 做分区过滤。该表不适合承载发货量或物流时效等量值，相关指标应取物流发货相关表。库内存在若干同家族噪声表：备份表 `ods_lgs_warehouse_di_bak` 及带日期备份 `ods_lgs_warehouse_di_bak_20250301`、`ods_lgs_warehouse_di_bak_20251117`。上述表均为历史快照，其口径与时效不受保证，不得用于正式统计。正式取数一律以 `ods_lgs_warehouse_di` 为准。
