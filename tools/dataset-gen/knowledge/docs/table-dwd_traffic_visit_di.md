# 表说明：dwd_traffic_visit_di（DWD-商品访问明细）

## 概述

`dwd_traffic_visit_di` 是商品访问的明细宽表，属于 DWD 层，表注释为 DWD-商品访问明细，当前 95,138 行，一条访问记录一行。它由访问日志 `ods_log_visit_di` 关联商品维表 `dim_sku` 加工，补齐了品牌和一级品类。给新同事交代一句：这张表记录的是一次次访问行为，访客数是对访问人去重的结果，不是行数本身，统计 UV 要用 `COUNT(DISTINCT visitor_id)` 而不是 `COUNT(*)`。

## 字段

- `visit_id` 是 bigint 的访问记录ID，是本表主键。
- `visitor_id` 是 bigint 的访问用户ID，算访客数要对它去重。
- `sku_id` 是 bigint 的商品SKU ID，标识被访问的商品。
- `channel` 是 varchar(20) 的访问渠道，是分析来源的维度。
- `stay_seconds` 是 int 的停留秒数，衡量访问深度，算平均停留时长时用它。
- `brand_name` 是 varchar(50) 的品牌，来自 `dim_sku`，方便按品牌聚合。
- `category1_id` 是 int 的一级品类ID。
- `category1_name` 是 varchar(50) 的一级品类名称，与 ID 一同冗余进来。
- `visit_date` 是 date 的访问日期，是业务时间。
- `dt` 是 date 的分区日期，用于分区裁剪，别当访问时间用。

## 上下游

`dwd_traffic_visit_di` 的上游是 `ods_log_visit_di` 和 `dim_sku`。它的下游很多，面向 ADS 的 `ads_traffic_conversion_daily`，以及一级品类、SKU、访问渠道、访客四个粒度的日/周/月汇总，如 `dws_traffic_cate1_visit_1d`、`dws_traffic_sku_visit_1m`、`dws_traffic_vchannel_visit_1w`、`dws_traffic_visitor_visit_1d` 等。

## 使用注意

`dwd_traffic_visit_di` 适合做访问行为的灵活下钻，比如按渠道、品类、SKU 看浏览量和访客数，或用 `stay_seconds` 算平均停留。固定口径的流量汇总建议用对应 DWS/ADS 表。核心坑点：访客数必须对 `visitor_id` 去重且不能跨天相加；区分 `visit_date` 与 `dt`。本家族有历史归档表 `dwd_traffic_visit_di_his`、备份表 `dwd_traffic_visit_di_bak`、带日期备份 `dwd_traffic_visit_di_bak_20250101` 和测试表 `test_dwd_traffic_visit_di`，都是旧快照、时点副本或调试产物，统计一律用 `dwd_traffic_visit_di`。
