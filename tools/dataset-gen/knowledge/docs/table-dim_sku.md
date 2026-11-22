# 表说明：dim_sku（DIM-商品维表（含品牌、两级品类））

## 概述

`dim_sku` 是商品的维表，属于 DIM 层，表注释为 DIM-商品维表（含品牌、两级品类），当前 300 行，一个 SKU 一行。它把商品基础信息、品牌和两级品类拍平在一张表里，由 `ods_item_sku_di`、`ods_item_brand_di`、`ods_item_category_di` 关联加工。对新同事来说，凡是需要给带 `sku_id` 的事实补充品牌或品类名称，关联这张表就够了，不用自己再去拼品类层级。

## 字段

- `sku_id` 是 bigint 的商品SKU ID，是本表主键，也是关联事实表的键。
- `sku_name` 是 varchar(100) 的商品名称，用于展示。
- `price` 是 decimal(10,2) 的当前售价，单位元，注意它是当前价快照，不代表历史成交价，算历史金额不要用它。
- `sku_status` 是 varchar(20) 的商品状态，反映商品当前是否在售等状态。
- `brand_id` 是 int 的品牌ID。
- `brand_name` 是 varchar(50) 的品牌名称，按品牌分析时直接用它更方便。
- `brand_level` 是 varchar(10) 的品牌等级。
- `category2_id` 是 int 的二级品类ID。
- `category2_name` 是 varchar(50) 的二级品类名称。
- `category1_id` 是 int 的一级品类ID。
- `category1_name` 是 varchar(50) 的一级品类名称，一级和二级品类都已拍平，可直接按任意层级聚合。

## 上下游

`dim_sku` 的上游是 `ods_item_sku_di`（商品）、`ods_item_brand_di`（品牌）和 `ods_item_category_di`（品类），加工时以 SKU 为主，关联出品牌等级和一级、二级品类。它的下游包括 `dwd_item_review_di` 和 `dwd_traffic_visit_di`，这两张明细表在加工时都会关联 `dim_sku` 补齐品牌和品类。

## 使用注意

`dim_sku` 适合给事实表补充商品、品牌、品类属性，也适合做商品清单、品类层级的查询。要记住 `price` 是当前售价快照，算历史销售金额应从订单明细取成交价，不要用维表的 `price` 反推。它是维表，不含销量、访问等度量，不能直接用来统计。本家族有备份表 `dim_sku_bak`、带日期备份 `dim_sku_bak_20250426` 和测试表 `test_dim_sku`，都是时点副本或调试产物，可能缺列或过期，关联和查询一律用 `dim_sku`。
