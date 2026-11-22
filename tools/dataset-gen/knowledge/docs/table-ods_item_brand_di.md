# 表说明：ods_item_brand_di（ODS-商品品牌表）

## 概述

`ods_item_brand_di` 是 ODS 层的商品品牌贴源表，原样承接业务侧品牌数据。

- 粒度：一个品牌一行。
- 当前规模：12 行。
- 特点：比业务表多 `etl_time`、`dt` 两个技术字段。
- 适用场景：向上层维度加工供数，或做品牌维度查询。

## 字段

- `brand_id`（int）：品牌 ID，品牌的唯一标识。
- `brand_name`（varchar(50)）：品牌名称，与 `item_sku.brand_name` 对应，按名称关联注意两侧一致。
- `origin_country`（varchar(20)）：品牌所属国家或地区。
- `founded_year`（smallint）：品牌创立年份，为年份值。
- `brand_level`（varchar(10)）：品牌等级，取值 S、A、B。
- `etl_time`（datetime）：ETL 同步时间，技术字段，非业务时间。
- `dt`（date）：分区日期，用于分区过滤。

## 上下游

`ods_item_brand_di` 的上游是 `item_brand`，经 ETL 同步落库。

- 下游：`dim_sku`，由它承接品牌属性加工进商品维度。

## 使用注意

`ods_item_brand_di` 适合做品牌维度查询，以及向下游维度供数。

- 查询建议：带 `dt` 做分区过滤。
- 噪声表：备份表 `ods_item_brand_di_bak`、带日期备份如 `ods_item_brand_di_bak_20250513`、旧版本 `ods_item_brand_di_v2`、测试表 `test_ods_item_brand_di`。
- 为什么不能用：历史快照、早期口径或调试数据，口径与时效都不保证。
- 正式取数只认 `ods_item_brand_di`。
