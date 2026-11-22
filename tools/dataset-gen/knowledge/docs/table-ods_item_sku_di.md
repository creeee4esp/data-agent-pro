# 表说明：ods_item_sku_di（ODS-商品SKU表）

## 概述

`ods_item_sku_di` 是 ODS 层的商品 SKU 贴源表，原样承接业务侧的商品主数据，当前 300 行，一行一个商品。相比业务表，它多了 `etl_time` 和 `dt` 两个技术字段。作为贴源的商品维度来源，它常被下游拿去搭建商品维表和订单明细宽表。要查某个商品叫什么、属于哪个品类、哪个品牌、售价和库存多少，从这里取即可。

## 字段

- `sku_id`（bigint）：商品 SKU ID，是主键，下游要带出商品信息都靠它关联。
- `sku_name`（varchar(100)）：商品名称，展示和检索用，做统计别拿名称当唯一键。
- `category_id`（int）：所属二级品类 ID，做品类汇总时用它关联品类维度。
- `brand_name`（varchar(50)）：品牌名称，按品牌分析时注意和品牌维度的取值一致。
- `price`（decimal(10,2)）：当前售价，单位元，是当前快照价，不代表历史成交价。
- `stock`（int）：当前库存数量，同样是实时快照，不能用来还原过去某天的库存。
- `sku_status`（varchar(20)）：商品状态，on_sale-在售、off_shelf-已下架，只看在售用它过滤。
- `created_time`（datetime）：上架时间，可按它看商品新老。
- `etl_time`（datetime）：ETL 同步时间，技术字段，不是业务时间，别用来做业务口径统计。
- `dt`（date）：分区日期，查询时用它裁剪分区。

## 上下游

`ods_item_sku_di` 的上游是 `item_sku`，通过 ETL 同步落到 ODS 层。它的下游有两张：`dim_sku`（商品维表）和 `dwd_trade_order_detail_di`（订单明细宽表），都靠它补齐商品属性。

## 使用注意

`ods_item_sku_di` 适合做商品属性查询、品类与品牌维度的商品归类，以及向下游供商品维度数据，查询时建议带 `dt` 做分区过滤。要注意 `price` 和 `stock` 都是当前快照，不适合做历史价格或历史库存回溯，那类需求得去对应时间点的数据找。库里同家族还有噪声表：备份表 `ods_item_sku_di_bak`、带日期备份如 `ods_item_sku_di_bak_20250529`、旧版本 `ods_item_sku_di_old`。它们是历史快照或早期结构，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_item_sku_di`。
