# 表说明：ods_item_category_di（ODS-商品品类表（两级））

## 概述

`ods_item_category_di` 是 ODS 层的商品品类贴源表，承接两级品类体系。

- 粒度：一个品类一行。
- 当前规模：22 行。
- 特点：含 `etl_time`、`dt` 两个技术字段。
- 适用场景：向上层供品类维度数据。

## 字段

- `category_id`（int）：品类 ID，品类的唯一标识。
- `category_name`（varchar(50)）：品类名称，同名跨层级时结合 `cate_level` 区分。
- `parent_id`（int）：父品类 ID，一级品类为空，二级指向其一级 `category_id`。
- `cate_level`（tinyint）：品类层级，1-一级品类、2-二级品类，取单层用它过滤。
- `etl_time`（datetime）：ETL 同步时间，技术字段，非业务时间。
- `dt`（date）：分区日期，用于分区过滤。

## 上下游

`ods_item_category_di` 的上游是 `item_category`，经 ETL 同步落库。

- 下游：`dim_sku` 与 `dwd_trade_order_detail_di`，由它们带出品类属性。

## 使用注意

`ods_item_category_di` 适合做品类命名、层级归属查询，以及向下游供品类维度。

- 查询建议：带 `dt` 做分区过滤。
- 不适合：承载交易或访问量值，那些在对应事实表。
- 噪声表：备份表 `ods_item_category_di_bak`。
- 为什么不能用：它是历史快照，口径与时效都不保证。
- 正式取数只认 `ods_item_category_di`。
