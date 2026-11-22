# 表说明：ods_log_visit_di（ODS-商品页访问日志）

## 概述

`ods_log_visit_di` 是 ODS 层的商品页访问日志贴源表，原样承接访问明细，当前 95,138 行，一行一条访问记录。相比业务表，它多了 `etl_time` 和 `dt` 两个技术字段。它是流量链路往上加工的起点，PV/UV、停留时长、渠道与商品维度的汇总都从这里延伸。这里要提醒一个容易误用的地方：`etl_time` 是同步时间，不是用户实际访问时间，做流量时间口径一定要用 `visit_time`，别用错字段。

## 字段

- `visit_id`（bigint）：访问记录 ID，单条访问的唯一标识。
- `visitor_id`（bigint）：访问用户 ID，即 `user_info.user_id`，按用户聚合访问用它。
- `sku_id`（bigint）：浏览的商品 SKU ID，按商品统计流量用它。
- `channel`（varchar(20)）：访问渠道，app、mini_program-小程序、web，按渠道分组用这些枚举值。
- `visit_time`（datetime）：访问时间，是流量统计应采用的业务时间字段。
- `stay_seconds`（int）：页面停留秒数，单位是秒，算人均停留需自行聚合。
- `etl_time`（datetime）：ETL 同步时间，技术字段，容易被误当访问时间，务必和 `visit_time` 区分。
- `dt`（date）：分区日期，查询时用它裁剪分区。

## 上下游

`ods_log_visit_di` 的上游是 `log_visit`，通过 ETL 同步落到 ODS 层。它的下游是 `dwd_traffic_visit_di`，由后者承接做流量明细层加工，再往上滚动成各粒度汇总。

## 使用注意

`ods_log_visit_di` 适合做访问明细级别的 PV/UV、停留时长、渠道与商品流量分析，查询时建议带 `dt` 做分区过滤。它数据量大且需自行聚合，日常报表宜取上层已算好的流量汇总，而不是每次都扫明细。库里同家族还有噪声表：备份表 `ods_log_visit_di_bak`、带日期备份如 `ods_log_visit_di_bak_20250115`、测试表 `test_ods_log_visit_di`。它们是历史快照或调试数据，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_log_visit_di`。
