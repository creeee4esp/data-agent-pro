# 表说明：dws_trade_cate1_order_1w（DWS-一级品类粒度下单汇总（周））

## 概述

`dws_trade_cate1_order_1w` 按周、按一级品类汇总下单情况，当前 371 行。`stat_date` 存每周的周一日期，一周一行。数据来自 `dwd_trade_order_detail_di`，口径与日表月表一致。比如看某品类最近 12 周走势，`WHERE category1_id = 1 ORDER BY stat_date DESC LIMIT 12` 即可。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配，查区间用它 `BETWEEN`。
- `category1_id`：一级品类ID，和 `stat_date`、`category1_name` 共同唯一，关联聚合建议用 ID。
- `category1_name`：一级品类名称，展示和排序常用，如 `ORDER BY category1_name`。
- `order_cnt`：下单数，按订单去重，可跨周相加，例如连续 4 周 `SUM(order_cnt)`。
- `item_qty`：购买件数，含未支付订单，口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不是全部下单金额。

## 上下游

来源是交易明细表 `dwd_trade_order_detail_di`，按周一日期、`category1_id`、`category1_name` 分组聚合写入，当前无下游表。更粗汇总一般直接在本表上再聚合。

## 使用注意

适合取一级品类的周走势和周区间汇总，比如「近 4 周各一级品类已支付金额排名」或「某品类每周下单数」。要精确到天请用日表，跨月对比用月表更方便。同家族有 6 张带日期备份（如 `dws_trade_cate1_order_1w_bak_20250215`、`dws_trade_cate1_order_1w_bak_20250507`），它们是历史快照，数据可能过期，取数只认本表，别误取到备份上。
