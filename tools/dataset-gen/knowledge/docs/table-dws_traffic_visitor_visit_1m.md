# 表说明：dws_traffic_visitor_visit_1m（DWS-访问用户粒度访问汇总（月））

## 概述

`dws_traffic_visitor_visit_1m` 是 DWS 层按访问用户逐月汇总的流量表，当前 20,075 行，每行是"一个月 + 一个访问用户"。要看某用户某月活跃到什么程度，直接查它就行。比如找 2025 年 3 月浏览量最高的用户，`WHERE stat_date = '2025-03-01' ORDER BY pv DESC LIMIT 10` 即可，不用回明细重算。

## 字段

- `stat_date`（date）：统计月，存月初日期，筛选某月用月初那天，例如 3 月写该月 1 号。
- `visitor_id`（bigint）：访问用户 ID，配合 `stat_date` 唯一确定一行，按它分组看单用户月度表现。
- `pv`（int）：该用户当月浏览量，访问次数累计，不去重，适合排月度活跃。
- `uv`（int）：该用户当月访客数，已去重，单用户粒度下一般为 1，别当成全站月活。
- `stay_seconds`（bigint）：该用户当月总停留秒数，算人均单次停留要除以 `pv`，单位是秒。

## 上下游

`dws_traffic_visitor_visit_1m` 的数据来自明细层 `dwd_traffic_visit_di`，按月初日期和 `visitor_id` 分组，记录数聚成 `pv`、去重访客聚成 `uv`、停留时间求和成 `stay_seconds`。目前没有登记下游表，可直接供分析取用。

## 使用注意

`dws_traffic_visitor_visit_1m` 适合做用户的月度活跃排行、月度浏览分布和停留统计，例如取某月按 `pv` 降序找高活跃用户，或按月统计有访问记录的用户数。不适合做日或周粒度拆解，那种需求请用对应粒度的汇总表。库里同家族还有噪声表：备份表 `dws_traffic_visitor_visit_1m_bak`、带日期备份如 `dws_traffic_visitor_visit_1m_bak_20250102`。它们是某次操作前的历史快照，口径和时效都不保证，不能拿来跑统计，正式取数只认 `dws_traffic_visitor_visit_1m`。
