# 表说明：dws_traffic_visitor_visit_1d（DWS-访问用户粒度访问汇总（日））

## 概述

`dws_traffic_visitor_visit_1d` 是 DWS 层按访问用户逐日汇总的流量表。

- 粒度：一天 + 一个访问用户一行。
- 当前规模：67,819 行。
- 适用场景：单用户的日活跃、日浏览与停留分析。

## 字段

- `stat_date`（date）：统计日期，按天一行，取某天数据用它做等值过滤。
- `visitor_id`（bigint）：访问用户 ID，与 `stat_date` 共同唯一确定一行。
- `pv`（int）：该用户当天浏览量，访问次数累计，不去重。
- `uv`（int）：该用户当天访客数，已去重；单用户粒度下通常为 1，别拿它当全站访客数。
- `stay_seconds`（bigint）：该用户当天总停留秒数，单位为秒。

## 上下游

`dws_traffic_visitor_visit_1d` 的上游是明细层 `dwd_traffic_visit_di`。

- 加工逻辑：按访问日期和 `visitor_id` 分组，`pv` 取记录数，`uv` 取去重访客数，`stay_seconds` 取停留时间之和。
- 下游：暂无登记的下游表，可直接取用。

## 使用注意

`dws_traffic_visitor_visit_1d` 适合做用户的日活跃明细、日浏览分布和停留时长统计。

- 不适合：周或月汇总，应改用对应粒度的表。
- 噪声表：备份表 `dws_traffic_visitor_visit_1d_bak`、带日期备份如 `dws_traffic_visitor_visit_1d_bak_20250108`、旧版本 `dws_traffic_visitor_visit_1d_v2`、测试表 `test_dws_traffic_visitor_visit_1d`。
- 为什么不能用：备份是历史快照，旧版本是早期口径，测试表是调试数据，口径与时效都不可靠。
- 正式取数只认 `dws_traffic_visitor_visit_1d`。
