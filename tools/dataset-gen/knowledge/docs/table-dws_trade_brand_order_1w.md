# 表说明：dws_trade_brand_order_1w（DWS-品牌粒度下单汇总（周））

## 概述

`dws_trade_brand_order_1w` 把下单明细按周、按品牌汇成一行，当前 636 行。`stat_date` 存的是每周的周一日期，一周一个点。数据从 `dwd_trade_order_detail_di` 聚合而来，口径与日表月表一致。举个例子，想看某品牌最近 8 周的下单走势，直接 `WHERE brand_name = 'xxx' ORDER BY stat_date DESC LIMIT 8` 即可。

## 字段

- `stat_date`：统计周，取周一日期，查某一周要用那一周的周一日期去匹配，比如查第 40 周就用该周周一的日期。
- `brand_name`：品牌名称，和 `stat_date` 共同唯一，同一周同一品牌一行。
- `order_cnt`：下单数，按订单去重计数，跨周相加可得区间下单总数，例如连续 4 周求 `SUM(order_cnt)`。
- `item_qty`：购买件数，含未支付订单，用它估销量时口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不要当成全部下单金额。

## 上下游

来源表是交易明细 `dwd_trade_order_detail_di`，按周一日期和 `brand_name` 分组聚合写入，当前没有下游表。要出更粗粒度的结果一般直接在本表上再汇总。

## 使用注意

适合取品牌周维度的走势和周区间汇总，比如「近 4 周各品牌已支付金额排名」或「某品牌每周下单数」。不适合精确到某一天，要日粒度请用日表；跨月对比建议用月表更省事。还要注意同家族的噪声表：一张备份 `dws_trade_brand_order_1w_bak`、8 张带日期备份（如 `dws_trade_brand_order_1w_bak_20250106`、`dws_trade_brand_order_1w_bak_20250408`），以及一张旧版本 `dws_trade_brand_order_1w_v2`。备份是历史快照、旧版本是改版前的遗留，都可能数据过期或口径不同，取数时只认本表，别误取到这些表上。
