# 表说明：item_sku（商品SKU表）

## 概述

`item_sku` 是 CORE 层的商品 SKU 表，记录每个在售或曾在售商品的基础属性，当前 300 行。它以 SKU 为粒度，一行一个商品，是商品维度的主数据来源。想知道某个商品叫什么、属于哪个品类、什么品牌、当前卖多少钱、还有多少库存，查它就对了。因为它是维表，常被交易、评价等链路关联上来补齐商品属性。

## 字段

- `sku_id`（bigint）：商品 SKU ID，是这张表的主键，别的表要带出商品信息都靠它关联。
- `sku_name`（varchar(100)）：商品名称，用于展示和检索，做统计时别拿名称当唯一键，还是以 `sku_id` 为准。
- `category_id`（int）：所属二级品类 ID，指向 `item_category.category_id`，要做品类汇总就靠它关联。
- `brand_name`（varchar(50)）：品牌名称，和 `item_brand.brand_name` 对应，按品牌分析时注意两侧取值一致。
- `price`（decimal(10,2)）：当前售价，单位是元，它是当前快照价，不代表历史成交价，算历史客单价要去订单明细取成交单价。
- `stock`（int）：当前库存数量，同样是实时快照，不能用来还原过去某天的库存。
- `sku_status`（varchar(20)）：商品状态，on_sale 表示在售、off_shelf 表示已下架，只看在售商品时用它过滤。
- `created_time`（datetime）：上架时间，可用来看商品新老、按上架时间分组。

## 上下游

`item_sku` 的数据来自业务系统（在线数据库），同步后进入数据平台，下游是 ODS 层的 `ods_item_sku_di`，由它承接再往上层加工。表内还有两条关联：`item_sku.category_id` 指向 `item_category.category_id`，`trade_order_item.sku_id` 指向 `item_sku.sku_id`，后者让订单明细能带出商品属性。

## 使用注意

`item_sku` 适合做商品属性查询、品类与品牌维度的商品归类，以及给交易、评价数据补齐商品信息。要注意 `price` 和 `stock` 都是当前快照，不适合做历史价格或历史库存的回溯分析，那类需求得去对应时间点的数据里找。库里同家族还有噪声表：备份表 `item_sku_bak`、带日期备份如 `item_sku_bak_20250724`、旧版本 `item_sku_v2`。它们是历史快照或早期结构，口径和时效都不保证，不能拿来跑统计，正式取数只认 `item_sku`。
