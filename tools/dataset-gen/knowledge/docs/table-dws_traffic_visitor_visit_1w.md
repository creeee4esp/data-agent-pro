# 表说明：dws_traffic_visitor_visit_1w（DWS-访问用户粒度访问汇总（周））

## 概述

`dws_traffic_visitor_visit_1w` 是 DWS 层按访问用户逐周汇总的流量表，当前 48,564 行，每行是"一周 + 一个访问用户"，周以周一为起点，`stat_date` 存周一日期。要看某用户在某一周的活跃情况，直接查它。比如找某周浏览量前十的用户，`WHERE stat_date = '2025-04-07' ORDER BY pv DESC LIMIT 10` 即可。

## 字段

- `stat_date`（date）：统计周，存该周周一日期，按周过滤要用周一那天。
- `visitor_id`（bigint）：访问用户 ID，配合 `stat_date` 唯一确定一行。
- `pv`（int）：该用户当周浏览量，访问次数累计，不去重。
- `uv`（int）：该用户当周访客数，已去重，单用户粒度下一般为 1，别当成全站周活。
- `stay_seconds`（bigint）：该用户当周总停留秒数，单位是秒，人均单次停留需除以 `pv`。

## 上下游

`dws_traffic_visitor_visit_1w` 的数据来自明细层 `dwd_traffic_visit_di`，按周一日期和 `visitor_id` 分组，记录数聚成 `pv`、去重访客聚成 `uv`、停留时间求和成 `stay_seconds`。目前没有登记下游表，可直接供分析取用。

## 使用注意

`dws_traffic_visitor_visit_1w` 适合做用户的周度活跃排行和周环比，例如取相邻两周按 `visitor_id` 对比浏览量变化。不适合做日或月粒度拆解，请用对应粒度的汇总表。库里同家族还有噪声表：备份表 `dws_traffic_visitor_visit_1w_bak`、带日期备份如 `dws_traffic_visitor_visit_1w_bak_20250111`、旧版本 `dws_traffic_visitor_visit_1w_v2`。它们是历史快照或早期口径，口径和时效都不保证，不能拿来跑统计，正式取数只认 `dws_traffic_visitor_visit_1w`。
