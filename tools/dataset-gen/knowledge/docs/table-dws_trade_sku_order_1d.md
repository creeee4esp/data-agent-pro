# 表说明：dws_trade_sku_order_1d（DWS-商品SKU 粒度下单汇总（日））

## 概述

`dws_trade_sku_order_1d` 按统计日期和 SKU 汇总每日下单情况，当前 17,191 行，是这个家族里行数较大的一张。数据来自 `dwd_trade_order_detail_di`，下单数去重、件数和已支付金额求和。要注意 `item_qty` 含未支付订单，和只算已支付的 `paid_amount` 分母不同，别直接相除当件单价。

## 字段

- `sku_id`：商品SKU ID，`bigint` 类型，和 `stat_date` 共同构成行粒度，是关联商品维表的主键。
- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`。
- `order_cnt`：下单数，按订单去重计数，可跨天相加。
- `item_qty`：购买件数，含未支付订单，口径偏大，看销量时注意。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不是全部下单金额。

## 上下游

来源是交易明细表 `dwd_trade_order_detail_di`，按 `order_date` 和 `sku_id` 分组聚合写入，当前没有登记下游表。需要品类、品牌等更粗维度时一般走对应维度的表，而不是在 SKU 表上再关联维表硬聚。

## 使用注意

适合做 SKU 的每日销量走势、热销 SKU 排行、日区间汇总，比如「某日 `paid_amount` 最高的前 20 个 SKU」。由于 SKU 很多，查询尽量带上日期范围避免全表扫。同家族的噪声表有备份 `dws_trade_sku_order_1d_bak`、2 张带日期备份（如 `dws_trade_sku_order_1d_bak_20250613`、`dws_trade_sku_order_1d_bak_20251031`）和旧版本 `dws_trade_sku_order_1d_v1`。备份是历史快照、旧版本是改版遗留，数据可能残缺或口径不同，统计一律用本表，这是最容易取错的地方。
