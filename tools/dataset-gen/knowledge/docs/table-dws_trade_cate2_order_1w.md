# 表说明：dws_trade_cate2_order_1w（DWS-二级品类粒度下单汇总（周））

## 概述

`dws_trade_cate2_order_1w` 按周、按二级品类汇总下单，当前 795 行。`stat_date` 存周一日期，一周一行。数据来自 `dwd_trade_order_detail_di`，口径和日表月表一致。比如看某二级品类最近 8 周走势，`WHERE category2_id = 101 ORDER BY stat_date DESC LIMIT 8`。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配，查区间用 `BETWEEN`。
- `category2_id`：二级品类ID，和 `stat_date`、`category2_name` 共同唯一，关联聚合建议用 ID。
- `category2_name`：二级品类名称，展示排序常用，不同一级品类下可能重名，分组最好带 ID。
- `order_cnt`：下单数，按订单去重，可跨周相加，例如连续 4 周 `SUM(order_cnt)`。
- `item_qty`：购买件数，含未支付订单，口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分。

## 上下游

来源是交易明细表 `dwd_trade_order_detail_di`，按周一日期、`category2_id`、`category2_name` 分组聚合写入，当前无下游表。更粗汇总直接在本表上再聚合。

## 使用注意

适合取二级品类周走势和周区间汇总，比如「近 4 周各二级品类已支付金额排名」。要日粒度用日表，跨月对比用月表。同家族有一张备份 `dws_trade_cate2_order_1w_bak`，是历史快照，数据可能过期，取数只认本表，别误取到备份上。
