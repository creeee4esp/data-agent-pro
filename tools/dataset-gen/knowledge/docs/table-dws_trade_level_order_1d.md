# 表说明：dws_trade_level_order_1d（DWS-会员等级粒度下单汇总（日））

## 概述

`dws_trade_level_order_1d` 按统计日期和会员等级汇总每日下单情况，当前 1,806 行。跟你说下背景：数据从交易明细 `dwd_trade_order_detail_di` 来，下单数按订单去重，件数和已支付金额求和，`member_level` 是会员等级。要留意 `item_qty` 含未支付订单，而 `paid_amount` 只算已支付，这俩口径不一样，别拿来直接相除。

## 字段

- `stat_date`：统计日期，按天，查某天用等值过滤，查区间用 `BETWEEN`。
- `member_level`：会员等级，`tinyint` 类型，是个编码值（比如 1 到 5 表示不同档），分析时注意它是等级码不是名称。
- `order_cnt`：下单数，按订单去重计数，可以跨天相加。
- `item_qty`：购买件数，包含未支付订单，用它看销量时口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不是全部下单金额。

## 上下游

数据来源是交易明细表 `dwd_trade_order_detail_di`，加工时按 `order_date` 和 `member_level` 分组聚合写入本表，目前没有登记的下游表。想做更高层的汇总，一般直接在本表上再聚合。

## 使用注意

想看各会员等级的每日下单走势、日区间汇总、或者不同等级的消费对比，用这张表很合适。但它不带品类、SKU、用户这些维度，要拆到那些维度请改用对应的表。另外同家族里有几张噪声表：备份 `dws_trade_level_order_1d_bak`、带日期备份 `dws_trade_level_order_1d_bak_20251116`、测试表 `test_dws_trade_level_order_1d`。备份是历史快照，测试表是调试产物，数据可能过期或残缺，做统计时一律认准本表，别取到它们上面去。
