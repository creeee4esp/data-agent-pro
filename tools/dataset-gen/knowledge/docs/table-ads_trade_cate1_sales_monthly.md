# 表说明：ads_trade_cate1_sales_monthly（ADS-一级品类月度销售额）

## 概述

`ads_trade_cate1_sales_monthly` 是各一级品类每月的销售汇总，位于 ADS 层，表注释为 ADS-一级品类月度销售额，当前 84 行。它由订单明细宽表 `dwd_trade_order_detail_di` 按下单月和一级品类聚合而来，口径只算已支付的明细。新同事常问「某品类这个月卖了多少」，查这张表最直接。需要记住的是它的粒度只到一级品类，看不到品牌或二级品类。

## 字段

- `stat_month` 是 char(7) 的统计月份，表示下单所在的月份，是本表的时间轴。
- `category1_name` 是 varchar(50) 的一级品类名称，是除月份外的唯一拆分维度。
- `sales_amount` 是 decimal(16,2) 的销售额，指已支付明细金额之和，单位元，未支付的明细不计入。
- `item_qty` 是 int 的销售件数，同样只统计已支付的件数。

## 上下游

`ads_trade_cate1_sales_monthly` 的唯一上游是 `dwd_trade_order_detail_di`，加工时按下单日期的月份和一级品类分组，对已支付明细累加金额和件数。它没有下游表，是直接面向报表的末端结果。

## 使用注意

`ads_trade_cate1_sales_monthly` 适合做一级品类层面的月度销售对比和趋势分析，销售额与件数都可跨品类相加得总盘子。它不适合下钻到品牌、二级品类、省份或单品，也不要用它推算客单价之外的细分口径。另外它周边有备份表 `ads_trade_cate1_sales_monthly_bak`、一批带日期的备份如 `ads_trade_cate1_sales_monthly_bak_20250309`，以及测试表 `test_ads_trade_cate1_sales_monthly`。备份是某个时点的留存副本，测试表是开发调试用的，都不保证数据完整和最新，统计时一律只用 `ads_trade_cate1_sales_monthly`。
