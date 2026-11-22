# 表说明：dws_traffic_cate1_visit_1m（DWS-一级品类粒度访问汇总（月））

## 概述

`dws_traffic_cate1_visit_1m` 是一级品类维度的访问月汇总表，当前 84 行。先提醒一句，`stat_date` 存的是月初日期，代表整月，不是具体某天。数据从访问明细 `dwd_traffic_visit_di` 来，`pv` 是浏览量、`uv` 是去重访客数、`stay_seconds` 是总停留秒数，这里的 `uv` 已经是整月去重的结果。

## 字段

- `stat_date`：统计月，取月初日期，按月筛选时用月初日期去匹配。
- `category1_id`：一级品类ID，和 `stat_date`、`category1_name` 一起决定一行，关联聚合建议用 ID。
- `category1_name`：一级品类名称，展示和排序常用它。
- `pv`：浏览量，当月该品类的访问记录条数，可以跨月相加。
- `uv`：访客数，是整月去重后的访客数，别把多个月的 `uv` 直接相加，那会重复计数。
- `stay_seconds`：总停留秒数，`bigint` 类型，可以跨月相加，除以 `pv` 得平均停留时长。

## 上下游

数据来源是访问明细表 `dwd_traffic_visit_di`，加工时按月初日期、`category1_id`、`category1_name` 分组聚合写入本表，目前没有下游表。需要更粗维度的访问汇总时走对应维度的表。

## 使用注意

想看一级品类的月度流量走势、月度 PV 排名、月度平均停留时长，用这张表最省事，一月一个品类一行。要看某天某周就别用它，请改用日表或周表；跨月求去重访客数也不能直接把月 `uv` 相加，得回到明细重新去重。另外同家族里有一批噪声表：备份 `dws_traffic_cate1_visit_1m_bak`、4 张带日期备份（如 `dws_traffic_cate1_visit_1m_bak_20250723`、`dws_traffic_cate1_visit_1m_bak_20251001`）、测试表 `test_dws_traffic_cate1_visit_1m`。这些是历史快照或调试产物，数据未必准，做统计时一律认准本表。
