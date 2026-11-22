# 表说明：dws_traffic_cate1_visit_1d（DWS-一级品类粒度访问汇总（日））

## 概述

`dws_traffic_cate1_visit_1d` 按统计日期和一级品类汇总每日访问情况，当前 2,555 行。跟你说下口径：数据从访问明细 `dwd_traffic_visit_di` 来，`pv` 是浏览量（访问记录条数），`uv` 是去重后的访客数，`stay_seconds` 是总停留秒数。要留意 `uv` 是当天当品类去重的结果，跨天不能直接相加，这是流量类指标最常踩的坑。

## 字段

- `stat_date`：统计日期，按天，查某天用它等值过滤，查区间用 `BETWEEN`。
- `category1_id`：一级品类ID，和 `stat_date`、`category1_name` 一起决定一行，关联聚合建议用 ID。
- `category1_name`：一级品类名称，展示和排序常用它。
- `pv`：浏览量，是当天该品类的访问记录条数，可以跨天相加。
- `uv`：访客数，是当天当品类去重的访客数，跨天相加会重复计数，求区间 UV 要回到明细重新去重。
- `stay_seconds`：总停留秒数，`bigint` 类型，可以跨天相加，除以 `pv` 可得平均停留时长。

## 上下游

数据来源是访问明细表 `dwd_traffic_visit_di`，加工时按 `visit_date`、`category1_id`、`category1_name` 分组聚合写入本表，目前没有登记的下游表。需要更粗维度的访问汇总时走对应维度的表。

## 使用注意

想看一级品类的每日流量走势、品类间 PV 对比、平均停留时长（`stay_seconds` / `pv`），用这张表很合适。但要算一段时间的去重访客数，别直接 `SUM(uv)`，那会重复计数，得回到明细重新去重。另外同家族里有一批噪声表：备份 `dws_traffic_cate1_visit_1d_bak`、7 张带日期备份（如 `dws_traffic_cate1_visit_1d_bak_20250309`、`dws_traffic_cate1_visit_1d_bak_20250413`）、测试表 `test_dws_traffic_cate1_visit_1d`。备份是历史快照、测试表是调试产物，数据未必准，做统计时认准本表，别取到它们上面。
