# 表说明：dws_traffic_sku_visit_1m（DWS-商品SKU 粒度访问汇总（月））

## 概述

`dws_traffic_sku_visit_1m` 属于 DWS 层，把商品 SKU 的访问行为按自然月汇总成一张宽表，当前有 3,600 行。做商品级别的月度流量分析时，这张表基本够用：想看某个 `sku_id` 在哪个月被浏览得最多、月度访客数怎么走，直接查它就行，不用再回到明细去重算。每一行对应"一个月 + 一个 SKU"的组合，`stat_date` 存的是月初那一天的日期，方便你按月做分组和同比环比。

## 字段

- `stat_date`（date）：统计月，存的是月初日期，比如 2025 年 3 月的数据会写成该月 1 号，按月筛选时记得用月初日期而不是月末。
- `sku_id`（bigint）：商品 SKU ID，是这张表的业务主键之一，和它配合 `stat_date` 一起唯一确定一行。
- `pv`（int）：该 SKU 当月的浏览量，是访问次数的累计，同一个人多次浏览会被重复计入。
- `uv`（int）：该 SKU 当月的访客数，按去重后的访客统计，不能直接把多个月的 `uv` 相加当成跨月去重人数。
- `stay_seconds`（bigint）：该 SKU 当月的总停留秒数，想算人均停留时长要自己除以 `uv`，单位是秒不是分钟。

## 上下游

`dws_traffic_sku_visit_1m` 的数据来自明细层的 `dwd_traffic_visit_di`，加工时按月初日期和 `sku_id` 分组，把浏览记录数聚成 `pv`、把去重访客聚成 `uv`、把停留时间求和成 `stay_seconds`。目前它没有登记下游表，属于可以直接给分析师和报表取用的汇总结果。

## 使用注意

`dws_traffic_sku_visit_1m` 适合做 SKU 维度的月度流量排行、月度趋势和停留时长分析，不适合做日粒度或周粒度的拆解，那种需求要去找对应粒度的汇总表。库里还散落着几张同名家族的噪声表：备份表 `dws_traffic_sku_visit_1m_bak`、带日期的备份如 `dws_traffic_sku_visit_1m_bak_20250108`、旧版本 `dws_traffic_sku_visit_1m_v2`，以及测试表 `test_dws_traffic_sku_visit_1m`。它们要么是某次操作前的历史快照，要么是早期口径或调试用的数据，口径和时效都不保证，千万不要用来跑统计，正式取数只认 `dws_traffic_sku_visit_1m` 这一张。
