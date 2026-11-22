# 表说明：dws_traffic_vchannel_visit_1w（DWS-访问渠道粒度访问汇总（周））

## 概述

`dws_traffic_vchannel_visit_1w` 是 DWS 层按访问渠道逐周汇总的流量表。

- 粒度：一周 + 一个渠道一行。
- 周口径：以周一为一周起点，`stat_date` 存周一日期。
- 当前规模：159 行。
- 适用场景：渠道的周度流量观察与周环比。

## 字段

- `stat_date`（date）：统计周，存该周周一日期，按周过滤要用周一日期。
- `channel`（varchar(20)）：访问渠道，按它分组得到各渠道周度表现。
- `pv`（int）：该渠道当周浏览量，访问次数累计，不去重。
- `uv`（int）：该渠道当周访客数，已去重，跨周相加不等于去重人数。
- `stay_seconds`（bigint）：该渠道当周总停留秒数，单位为秒，人均停留需除以 `uv`。

## 上下游

`dws_traffic_vchannel_visit_1w` 的上游是明细层 `dwd_traffic_visit_di`。

- 加工逻辑：按周一日期和渠道分组，`pv` 取记录数，`uv` 取去重访客数，`stay_seconds` 取停留时间之和。
- 下游：暂无登记的下游表，可直接取用。

## 使用注意

`dws_traffic_vchannel_visit_1w` 适合做渠道的周度趋势、周度占比和周环比。

- 不适合：日或月粒度拆解，应改用对应粒度的汇总表。
- 噪声表：旧版本 `dws_traffic_vchannel_visit_1w_old`。
- 为什么不能用：它是早期口径残留，口径和时效都不可靠。
- 正式取数只认 `dws_traffic_vchannel_visit_1w`。
