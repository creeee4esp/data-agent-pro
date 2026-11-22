# 表说明：dws_traffic_cate1_visit_1w（DWS-一级品类粒度访问汇总（周））

## 概述

`dws_traffic_cate1_visit_1w` 是一级品类维度的访问周汇总表，当前 371 行。`stat_date` 存每周的周一日期，一周一行。数据从访问明细 `dwd_traffic_visit_di` 来，`pv` 是浏览量、`uv` 是去重访客数、`stay_seconds` 是总停留秒数，这里的 `uv` 是整周去重后的值。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期去匹配，查区间用它 `BETWEEN`。
- `category1_id`：一级品类ID，和 `stat_date`、`category1_name` 一起决定一行，关联聚合建议用 ID。
- `category1_name`：一级品类名称，展示和排序常用它。
- `pv`：浏览量，当周该品类的访问记录条数，可以跨周相加。
- `uv`：访客数，整周去重后的访客数，别把多周的 `uv` 直接相加，那会重复计数。
- `stay_seconds`：总停留秒数，`bigint` 类型，可以跨周相加，除以 `pv` 得平均停留时长。

## 上下游

数据来源是访问明细表 `dwd_traffic_visit_di`，加工时按周一日期、`category1_id`、`category1_name` 分组聚合写入本表，目前没有下游表。需要更粗维度的访问汇总时走对应维度的表。

## 使用注意

想看一级品类的周流量走势、周 PV 对比、周平均停留时长，用这张表合适，一周一个品类一行。要精确到天请用日表，跨月对比用月表更顺手；跨周求去重访客数不能直接把周 `uv` 相加，得回到明细重新去重。另外同家族里有一批噪声表：历史归档 `dws_traffic_cate1_visit_1w_his`、备份 `dws_traffic_cate1_visit_1w_bak`、6 张带日期备份（如 `dws_traffic_cate1_visit_1w_bak_20250315`、`dws_traffic_cate1_visit_1w_bak_20250328`）。这些是归档或快照，数据可能过期，做统计时一律认准本表，别图省事取到它们上面。
