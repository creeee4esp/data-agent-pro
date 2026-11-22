# 表说明：lgs_warehouse（仓库表）

## 概述

`lgs_warehouse` 是 CORE 层的仓库维表，记录各发货仓库的基础信息。

- 粒度：一个仓库一行。
- 当前规模：6 行。
- 适用场景：仓库名称、所属大区与省份的维度关联。

## 字段

- `warehouse_id`（int）：仓库 ID，仓库的唯一标识，关联时以它为准。
- `warehouse_name`（varchar(50)）：仓库名称，用于展示。
- `region`（varchar(10)）：所属大区，取值为华东、华南、华北、华中、西南、西北，按大区汇总用它。
- `province`（varchar(20)）：仓库所在省份。

## 上下游

`lgs_warehouse` 的上游是业务系统（在线数据库）。

- 下游：ODS 层的 `ods_lgs_warehouse_di`，由它承接再往上层加工。
- 关联：`lgs_shipment.warehouse_id` 指向 `lgs_warehouse.warehouse_id`，发货单据此带出仓库的大区与省份。

## 使用注意

`lgs_warehouse` 适合作为维度表给发货数据补齐仓库名称、大区、省份。

- 不适合：承载发货量或物流时效，那些量值在 `lgs_shipment` 里。
- 噪声表：备份表 `lgs_warehouse_bak`、带日期备份如 `lgs_warehouse_bak_20250214`、旧版本 `lgs_warehouse_v1`。
- 为什么不能用：备份是历史快照，旧版本是早期结构，口径与时效都不保证。
- 正式取数只认 `lgs_warehouse`。
