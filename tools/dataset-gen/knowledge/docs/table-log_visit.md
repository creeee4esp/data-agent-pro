# 表说明：log_visit（商品页访问日志）

## 概述

`log_visit` 是 CORE 层的商品页访问日志，记录用户浏览商品页的明细行为。

- 粒度：一条访问记录一行。
- 当前规模：95,138 行。
- 适用场景：PV/UV、停留时长、渠道与商品维度的流量明细分析。

## 字段

- `visit_id`（bigint）：访问记录 ID，单条访问的唯一标识。
- `visitor_id`（bigint）：访问用户 ID，即 `user_info.user_id`，按用户聚合访问用它。
- `sku_id`（bigint）：浏览的商品 SKU ID，按商品统计流量用它。
- `channel`（varchar(20)）：访问渠道，取值 app、mini_program-小程序、web，按渠道分组用这些枚举值。
- `visit_time`（datetime）：访问时间，按日期或时段统计用它。
- `stay_seconds`（int）：页面停留秒数，单位是秒，算人均停留需自行聚合。

## 上下游

`log_visit` 的上游是业务系统（在线数据库）。

- 下游：ODS 层的 `ods_log_visit_di`，由它承接再往上层加工。

## 使用注意

`log_visit` 适合做访问明细级别的 PV/UV、停留时长、渠道与商品流量分析。

- 不适合：直接当汇总结果用，量大且需自行聚合，日常报表宜取对应的 DWS 汇总表。
- 噪声表：备份表 `log_visit_bak`、带日期备份如 `log_visit_bak_20250112`、日分表（`log_visit_20250101 ～ log_visit_20251231`，如 `log_visit_20250101`）、旧版本 `log_visit_v2`、月分表（`log_visit_202301 ～ log_visit_202512`，如 `log_visit_202301`）、测试表 `test_log_visit`。
- 为什么不能用：这些是历史快照、按期拆分存档、早期口径或调试数据，口径与完整性都不保证。
- 正式取数只认 `log_visit`。
