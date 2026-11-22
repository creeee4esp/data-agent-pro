# 表说明：dws_trade_prov_order_1d（DWS-收货省份粒度下单汇总（日））

## 概述

`dws_trade_prov_order_1d` 按统计日期和收货省份汇总每日下单情况，当前 5,278 行。数据来自 `dwd_trade_order_detail_di`，下单数去重、件数和已支付金额求和。比如看某天各省份下单情况，`WHERE stat_date = '2025-04-20'`。

## 字段

- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`。
- `receiver_province`：收货省份，和 `stat_date` 共同唯一，按省份过滤如 `WHERE receiver_province = '广东省'`。
- `order_cnt`：下单数，按订单去重计数，可跨天相加，例如 `SUM(order_cnt)` 求区间总数。
- `item_qty`：购买件数，含未支付订单，口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不是全部下单金额。

## 上下游

来源是交易明细表 `dwd_trade_order_detail_di`，按 `order_date` 和 `receiver_province` 分组聚合写入，当前没有登记下游表。更高层汇总一般直接在本表上再聚合。

## 使用注意

适合做省份的每日下单趋势、日区间汇总和省份间对比，比如「某日各省份已支付金额排名」或「某省份近 30 天 `SUM(order_cnt)`」。不适合城市、品类或用户维度拆解，请改用对应粒度的表。同家族噪声表有备份 `dws_trade_prov_order_1d_bak`、带日期备份 `dws_trade_prov_order_1d_bak_20250420`、测试表 `test_dws_trade_prov_order_1d`。备份是历史快照、测试表是调试产物，数据可能残缺或过期，统计一律用本表，别取到它们上面。
