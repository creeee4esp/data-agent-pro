# 表说明：dws_trade_level_order_1m（DWS-会员等级粒度下单汇总（月））

## 概述

`dws_trade_level_order_1m` 按统计月和会员等级汇总下单情况，当前 60 行。`stat_date` 存的是月初日期而非具体某天，这点容易被当成日表，要先分清。数据来自 `dwd_trade_order_detail_di`，下单数去重、件数和已支付金额求和，口径与日表周表一致。

## 字段

- `stat_date`：统计月，取月初日期，按月筛选时必须用月初日期匹配，别用月中某天。
- `member_level`：会员等级，`tinyint` 编码值，是等级码而非名称，分析时注意它的含义。
- `order_cnt`：下单数，当月去重后的订单计数，可跨月相加。
- `item_qty`：购买件数，含未支付订单，口径偏大，看销量要注意。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不是当月下单总额。

## 上下游

来源是交易明细表 `dwd_trade_order_detail_di`，按月初日期和 `member_level` 分组聚合写入，当前没有下游表。更高层汇总一般直接在本表上再聚合。

## 使用注意

适合做会员等级的月度趋势、月度同比环比和各等级月度消费对比。要日或周粒度请改用对应的日表、周表。这里要重点提醒同家族的噪声表：备份 `dws_trade_level_order_1m_bak`、6 张带日期备份（如 `dws_trade_level_order_1m_bak_20250227`、`dws_trade_level_order_1m_bak_20250718`）、测试表 `test_dws_trade_level_order_1m`。备份是历史快照、测试表是调试产物，数据可能过期或不全，统计一律用本表，最容易出错的就是手误取到备份表。
