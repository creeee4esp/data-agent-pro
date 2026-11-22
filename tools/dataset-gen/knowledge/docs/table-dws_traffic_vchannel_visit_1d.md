# 表说明：dws_traffic_vchannel_visit_1d（DWS-访问渠道粒度访问汇总（日））

## 概述

`dws_traffic_vchannel_visit_1d` 是 DWS 层按访问渠道逐日汇总的流量表，当前 1,095 行，每行对应"一天 + 一个渠道"。想看某天各渠道带来多少浏览和访客，直接查它即可。比如取 2025 年某一天 app、web 的 `pv`、`uv` 对比，一句 `WHERE stat_date = '2025-04-05'` 就能拿到当天全部渠道的结果。

## 字段

- `stat_date`（date）：统计日期，按天一行，取某天数据用它做等值过滤即可。
- `channel`（varchar(20)）：访问渠道，想看单渠道趋势就 `WHERE channel = 'app'` 之类过滤，想看占比就按它分组。
- `pv`（int）：当天该渠道浏览量，是访问次数累计，不去重，适合算渠道曝光。
- `uv`（int）：当天该渠道访客数，已去重，跨天相加不等于去重人数，别直接 SUM 当独立访客。
- `stay_seconds`（bigint）：当天该渠道总停留秒数，算人均停留就 `stay_seconds / uv`，单位是秒。

## 上下游

`dws_traffic_vchannel_visit_1d` 的数据来自明细层 `dwd_traffic_visit_di`，按访问日期和渠道分组，记录数聚成 `pv`、去重访客聚成 `uv`、停留时间求和成 `stay_seconds`。目前没有登记下游表，是可直接取用的汇总结果。

## 使用注意

`dws_traffic_vchannel_visit_1d` 适合做渠道的日粒度趋势、渠道间日对比和日环比，例如取一段日期区间按 `channel` 看每天 `pv` 曲线。要做周或月汇总，去用对应粒度的表，不要自己把日表硬加。库里同家族还有一堆噪声表：备份表 `dws_traffic_vchannel_visit_1d_bak`、带日期备份如 `dws_traffic_vchannel_visit_1d_bak_20250405`、旧版本 `dws_traffic_vchannel_visit_1d_old`、测试表 `test_dws_traffic_vchannel_visit_1d`。这些是历史快照、早期口径或调试数据，取数时千万别碰，正式统计只认 `dws_traffic_vchannel_visit_1d`。
