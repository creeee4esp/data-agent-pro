# 表说明：ads_trade_province_sales_monthly（ADS-省份月度销售额）

## 概述

`ads_trade_province_sales_monthly` 是按收货省份统计的月度销售额，位于 ADS 层，表注释为 ADS-省份月度销售额，当前 192 行。它由订单明细宽表 `dwd_trade_order_detail_di` 按下单月和收货省份聚合而来，口径只算已支付明细。要点出一处容易误会的地方：拆分维度是收货省份，不是买家常住省份，跨省购买时会落在收货地所在省。

## 字段

- `stat_month` 是 char(7) 的统计月份，按下单月归属，是本表的时间轴。
- `receiver_province` 是 varchar(20) 的收货省份，按收货地而非买家归属地划分，是除月份外的唯一维度。
- `sales_amount` 是 decimal(16,2) 的销售额，单位元，只统计已支付明细金额，未支付的不计入。

## 上下游

`ads_trade_province_sales_monthly` 的唯一上游是 `dwd_trade_order_detail_di`，加工时按下单日期的月份和收货省份分组，对已支付明细累加销售额。它没有下游表，是面向报表的末端结果。

## 使用注意

`ads_trade_province_sales_monthly` 适合做省份级别的月度销售对比和地域分布分析，`sales_amount` 可跨省相加得总额。它不适合下钻到城市、品类或买家，也不要误把 `receiver_province` 当成买家常住省份使用。本家族有备份表 `ads_trade_province_sales_monthly_bak` 和一批带日期的备份如 `ads_trade_province_sales_monthly_bak_20250101`，它们是某时点的留存副本，不随任务更新、不保证完整，统计一律用 `ads_trade_province_sales_monthly`。
