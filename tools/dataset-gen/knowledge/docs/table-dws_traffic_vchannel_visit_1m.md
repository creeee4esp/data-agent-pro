# 表说明：dws_traffic_vchannel_visit_1m（DWS-访问渠道粒度访问汇总（月））

## 概述

`dws_traffic_vchannel_visit_1m` 是 DWS 层按访问渠道逐月汇总的流量表，当前 36 行，每行是"一个月 + 一个渠道"的组合。它用来看各渠道的月度流量规模和走势，`stat_date` 存月初日期。需要特别说明：行数很少不代表数据缺失，而是渠道数本来就不多、月份有限，别误以为漏算。

## 字段

- `stat_date`（date）：统计月，存月初日期，按月筛选务必用月初那天，用月末会一条也查不到——这是最容易踩的坑。
- `channel`（varchar(20)）：访问渠道，按它分组即可得到各渠道的月度占比。
- `pv`（int）：该渠道当月浏览量，访问次数累计，不去重。
- `uv`（int）：该渠道当月访客数，已去重；跨月把多行 `uv` 相加并不是去重人数，容易重复计人。
- `stay_seconds`（bigint）：该渠道当月总停留秒数，单位是秒，人均停留需除以 `uv`。

## 上下游

`dws_traffic_vchannel_visit_1m` 的数据来自明细层 `dwd_traffic_visit_di`，按月初日期和渠道分组，记录数聚成 `pv`、去重访客聚成 `uv`、停留时间求和成 `stay_seconds`。目前没有登记下游表，可直接供分析取用。

## 使用注意

`dws_traffic_vchannel_visit_1m` 适合做渠道的月度趋势、月度占比和同比环比，不适合做日或周粒度拆解，那种需求请用对应粒度的汇总表。库里同家族还有带日期的备份表，例如 `dws_traffic_vchannel_visit_1m_bak_20250112`、`dws_traffic_vchannel_visit_1m_bak_20251009`，它们是某次操作前留下的历史快照，口径和时效都不保证，不能拿来跑统计。正式取数只认 `dws_traffic_vchannel_visit_1m` 这一张。
