# 表说明：ads_traffic_conversion_daily（ADS-每日访问转化）

## 概述

`ads_traffic_conversion_daily` 是每日访问到下单的转化汇总，位于 ADS 层，表注释为 ADS-每日访问转化，当前 365 行，一年每日一行。它由访问明细 `dwd_traffic_visit_di` 和订单明细 `dwd_trade_order_detail_di` 按日期关联加工。要特别留意一点：访客数和下单用户数都是按天去重的，跨天分析绝不能把多天的值直接相加。

## 字段

- `stat_date` 是 date 的统计日期，是本表唯一的粒度，每天一行。
- `uv` 是 int 的访客数，为当日去重访客，不能跨日累加。
- `buyer_cnt` 是 int 的下单用户数，为当日去重下单用户，同样不能跨日累加。
- `conversion_rate` 是 decimal(8,4) 的转化率，等于 `buyer_cnt` 除以 `uv`，已算好可直接取用，不要把多天的 `conversion_rate` 简单平均来当作区间转化率。

## 上下游

`ads_traffic_conversion_daily` 的数据来自 `dwd_traffic_visit_di`（提供去重访客数）与 `dwd_trade_order_detail_di`（提供去重下单用户数），加工时按日期将二者左连接并相除得到转化率。它没有下游表，是面向报表的末端结果。

## 使用注意

`ads_traffic_conversion_daily` 适合看每日访客、下单用户和转化率的走势。它不适合按渠道、品类或省份拆分，那些需求要用对应的 DWS 流量表。两个关键坑：`uv` 与 `buyer_cnt` 是去重值不可跨日相加；要算区间转化率不能平均日值，需回到明细重新去重再相除。本家族有备份表 `ads_traffic_conversion_daily_bak`、带日期备份 `ads_traffic_conversion_daily_bak_20250620` 和测试表 `test_ads_traffic_conversion_daily`，均为时点副本或调试产物，不保证完整最新，统计一律用 `ads_traffic_conversion_daily`。
