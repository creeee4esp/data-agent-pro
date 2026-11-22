# 表说明：dws_trade_cate1_order_1d（DWS-一级品类粒度下单汇总（日））

## 概述

`dws_trade_cate1_order_1d` 按统计日期和一级品类汇总每日下单情况，当前 2,542 行。数据来自 `dwd_trade_order_detail_di`，口径为下单数去重、购买件数与已支付金额求和。例如看某天各一级品类的下单情况，直接 `WHERE stat_date = '2025-06-01'` 即可。

## 字段

- `stat_date`：统计日期，按天，取某天数据用它等值过滤，取区间用它 `BETWEEN`。
- `category1_id`：一级品类ID，和 `stat_date`、`category1_name` 一起构成行粒度，做关联或聚合时用 ID 更稳。
- `category1_name`：一级品类名称，展示和排序常用它，比如 `ORDER BY category1_name`。
- `order_cnt`：下单数，按订单去重计数，可跨天相加，例如 `SUM(order_cnt)` 求区间下单总数。
- `item_qty`：购买件数，含未支付订单，口径偏大，估销量时注意。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不是全部下单金额。

## 上下游

来源是交易明细表 `dwd_trade_order_detail_di`，按 `order_date`、`category1_id`、`category1_name` 分组聚合写入，当前没有登记下游表。更高层汇总一般直接在本表上再聚合。

## 使用注意

适合做一级品类的每日下单趋势、日区间汇总和品类间对比，比如「某日各一级品类已支付金额排名」或「某品类近 30 天 `SUM(order_cnt)`」。不适合做二级品类、SKU 或用户维度拆解，请改用对应粒度的表。同家族还有噪声表：备份 `dws_trade_cate1_order_1d_bak`、5 张带日期备份（如 `dws_trade_cate1_order_1d_bak_20250427`、`dws_trade_cate1_order_1d_bak_20250806`）和测试表 `test_dws_trade_cate1_order_1d`。备份是历史快照、测试表是调试产物，数据可能残缺或过期，统计一律用本表，别取到它们上面。
