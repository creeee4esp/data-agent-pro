# 表说明：ads_trade_gmv_monthly（ADS-月度GMV报表）

## 概述

`ads_trade_gmv_monthly` 是全站 GMV 的月度汇总，位于 ADS 层，表注释为 ADS-月度GMV报表，当前 12 行，正好对应一年每月一行。它由订单明细宽表 `dwd_trade_order_detail_di` 按下单月聚合得到，是看月度大盘最直接的表。要注意一点：它不含任何维度拆分，只有全站总量，按品类或省份看月度销售要用对应的 ADS 月报表。

## 字段

- `stat_month` 是 char(7) 的统计月份，形如 2025-06，是本表唯一的粒度，按下单月归属。
- `gmv` 是 decimal(16,2) 的 GMV，定义为已支付订单明细金额之和，单位元。
- `paid_order_cnt` 是 int 的支付订单数，为当月已支付的去重订单数。
- `paid_buyer_cnt` 是 int 的支付买家数，为当月去重的支付买家数，注意这是按月去重的结果，不能把多个月的 `paid_buyer_cnt` 相加当作多月买家数，否则会重复计数。

## 上下游

`ads_trade_gmv_monthly` 的唯一上游是 `dwd_trade_order_detail_di`，加工时按下单日期的月份分组，对已支付明细汇总 GMV，并对订单和买家做去重计数。它没有下游表，是面向报表的末端结果。

## 使用注意

`ads_trade_gmv_monthly` 适合看月度 GMV、支付订单数的整体走势和环比，`gmv` 可按月求和得到区间合计。它不适合做维度拆分；`paid_buyer_cnt` 是去重值，跨月相加会高估买家规模，要算多月去重买家数必须回到明细。本家族还有历史归档表 `ads_trade_gmv_monthly_his` 和备份表 `ads_trade_gmv_monthly_bak`，前者是旧数据的留存快照，后者是某时点的副本，都不随任务更新、口径也可能不同，统计一律用 `ads_trade_gmv_monthly`，不要用归档或备份。
