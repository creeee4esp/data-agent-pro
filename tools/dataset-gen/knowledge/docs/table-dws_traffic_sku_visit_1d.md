# 表说明：dws_traffic_sku_visit_1d（DWS-商品SKU 粒度访问汇总（日））

## 概述

`dws_traffic_sku_visit_1d` 按统计日期和 SKU 汇总每日访问情况，当前 55,584 行，是本批里行数最大的一张。数据来自访问明细 `dwd_traffic_visit_di`，`pv` 是浏览量、`uv` 是去重访客数、`stay_seconds` 是总停留秒数。要特别注意 `uv` 是当天当 SKU 去重的结果，跨天不能直接相加，这是流量指标最容易误用的地方。

## 字段

- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`。
- `sku_id`：商品SKU ID，`bigint` 类型，和 `stat_date` 共同构成行粒度，是关联商品维表的主键。
- `pv`：浏览量，当天该 SKU 的访问记录条数，可以跨天相加。
- `uv`：访客数，当天当 SKU 去重的访客数，跨天相加会重复计数，求区间 UV 要回到明细去重。
- `stay_seconds`：总停留秒数，`bigint` 类型，可跨天相加，除以 `pv` 得平均停留时长。

## 上下游

来源是访问明细表 `dwd_traffic_visit_di`，按 `visit_date` 和 `sku_id` 分组聚合写入，当前没有登记下游表。需要品类等更粗维度的访问汇总时走对应维度的表。

## 使用注意

适合做 SKU 的每日访问热度、热门 SKU 排行（按 `pv`）、平均停留时长分析，比如「某日 `pv` 最高的前 50 个 SKU」。由于 SKU 很多行数大，查询务必带日期范围避免全表扫描；算区间去重访客数别直接 `SUM(uv)`，要回明细重新去重。同家族的噪声表有备份 `dws_traffic_sku_visit_1d_bak`、7 张带日期备份（如 `dws_traffic_sku_visit_1d_bak_20250107`、`dws_traffic_sku_visit_1d_bak_20250205`）和旧版本 `dws_traffic_sku_visit_1d_old`。备份是历史快照、旧版本是改版遗留，数据可能残缺或口径不同，统计一律用本表，这是最容易取错的地方。
