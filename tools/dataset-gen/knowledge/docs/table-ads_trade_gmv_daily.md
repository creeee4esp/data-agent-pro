# 表说明：ads_trade_gmv_daily（ADS-每日GMV报表）

## 概述

`ads_trade_gmv_daily` 为每日 GMV 及相关交易规模的汇总报表，归属 ADS 层，表注释为 ADS-每日GMV报表，当前共 365 行，覆盖整年每日一行。其数据由 `dws_trade_user_order_1d` 按统计日期汇总而成，用于日粒度的整体交易监控。使用时应注意，该报表不含任何维度拆分，仅提供全站口径的每日总量。

## 字段

- `stat_date` 为 date 类型的统计日期，是本报表唯一的粒度，每个日期对应一行。
- `gmv` 为 decimal(16,2) 类型的 GMV，定义为已支付订单明细金额之和，单位为元。
- `paid_order_cnt` 为 int 类型的支付订单数，统计当日已支付的订单数量。
- `paid_buyer_cnt` 为 int 类型的支付买家数，为当日去重后的支付买家数，属不可跨日直接相加的指标，跨天统计买家数需回到明细去重。

## 上下游

`ads_trade_gmv_daily` 的唯一上游为 `dws_trade_user_order_1d`，加工时按 stat_date 汇总用户日粒度订单数据得到当日 GMV、支付订单数与支付买家数。该报表无下游表，为面向监控与报表的末端结果。

## 使用注意

`ads_trade_gmv_daily` 适用于日粒度的 GMV、支付订单数走势监控，`gmv` 与 `paid_order_cnt` 可按日期区间求和得到区间合计。该报表不适用于按月、按省份、按品类等维度的拆分，此类需求应使用对应的 ADS 月报或 DWS 汇总表；`paid_buyer_cnt` 为去重值，跨日累加会因重复买家而高估，不可简单求和。此外，本家族存在带日期的备份表 `ads_trade_gmv_daily_bak_20250708`、`ads_trade_gmv_daily_bak_20251020`，其为特定时点的留存副本，不随任务更新，数据未必完整或最新，不得用于统计，口径一律以 `ads_trade_gmv_daily` 为准。
