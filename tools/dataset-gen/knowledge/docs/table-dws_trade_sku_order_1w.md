# 表说明：dws_trade_sku_order_1w（DWS-商品SKU 粒度下单汇总（周））

## 概述

`dws_trade_sku_order_1w` 按统计周和 SKU 汇总下单情况，当前 8,182 行。`stat_date` 存每周的周一日期，一周一行。数据来自 `dwd_trade_order_detail_di`，下单数去重、件数和已支付金额求和。要注意 `item_qty` 含未支付订单，和只算已支付的 `paid_amount` 口径不同，不要直接相除。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配，查区间用 `BETWEEN`。
- `sku_id`：商品SKU ID，`bigint` 类型，和 `stat_date` 共同构成行粒度，是关联商品维表的主键。
- `order_cnt`：下单数，按订单去重计数，可跨周相加。
- `item_qty`：购买件数，含未支付订单，口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不是全部下单金额。

## 上下游

来源是交易明细表 `dwd_trade_order_detail_di`，按周一日期和 `sku_id` 分组聚合写入，当前没有登记下游表。按品类、品牌等更粗维度汇总时走对应维度的表更合适。

## 使用注意

适合做 SKU 的周销量走势、周热销榜和周区间汇总，比如「近 4 周 `SUM(paid_amount)` 最高的 SKU」。SKU 多，查询尽量带日期范围。同家族的噪声表有备份 `dws_trade_sku_order_1w_bak`、4 张带日期备份（如 `dws_trade_sku_order_1w_bak_20250313`、`dws_trade_sku_order_1w_bak_20250330`）和旧版本 `dws_trade_sku_order_1w_v1`。备份是历史快照、旧版本是改版遗留，数据可能残缺或口径不同，统计一律用本表，这是最容易取错的地方。
