# 表说明：dwd_item_review_di（DWD-评价明细）

## 概述

`dwd_item_review_di` 是商品评价的明细宽表，属于 DWD 层，表注释为 DWD-评价明细，当前 16,845 行，一条评价一行。它由贴源评价表 `ods_item_review_di` 关联商品维表 `dim_sku` 加工，在评价基础上补齐了品牌和一级品类，是评价类汇总的共同源头。给新同事一个判断标准：凡是需要在评价粒度上灵活统计、或者汇总表里没有现成口径的评价分析，都从这张明细出发。

## 字段

- `review_id` 是 bigint 的评价ID，是本表主键。
- `sku_id` 是 bigint 的商品SKU ID，标识被评价的商品。
- `user_id` 是 bigint 的用户ID，标识评价人。
- `rating` 是 tinyint 的评分，是原始打分。
- `is_good` 是 tinyint 的是否好评，4星及以上记为好评，算好评率时用它求和即可，不用再对 `rating` 设阈值。
- `brand_name` 是 varchar(50) 的品牌，来自 `dim_sku`，方便直接按品牌聚合。
- `category1_id` 是 int 的一级品类ID。
- `category1_name` 是 varchar(50) 的一级品类名称，与 ID 一同冗余进来，便于按品类分析。
- `review_date` 是 date 的评价日期，是业务时间，做评价时间分析用它。
- `dt` 是 date 的分区日期，用于分区裁剪，和业务日期 `review_date` 含义不同，别拿它当评价时间。

## 上下游

`dwd_item_review_di` 的上游是 `ods_item_review_di` 和 `dim_sku`。它的下游很多，面向 ADS 的 `ads_item_review_score_monthly`，以及品牌、一级品类、SKU 三个粒度的日/周/月汇总表，如 `dws_item_brand_review_1d`、`dws_item_cate1_review_1m`、`dws_item_sku_review_1w` 等，都是从这张明细聚合而来。

## 使用注意

`dwd_item_review_di` 适合在评价粒度上做灵活统计，或下钻到 SKU、品牌、一级品类做评分和好评率分析，好评率用 `is_good` 求和除以评价数即可。常规的固定口径汇总建议优先用现成的 DWS/ADS 表，减少重复计算。要注意区分 `review_date`（业务日期）和 `dt`（分区日期）。本家族有备份表 `dwd_item_review_di_bak`、带日期备份 `dwd_item_review_di_bak_20250210`、旧版本表 `dwd_item_review_di_old` 和测试表 `test_dwd_item_review_di`，它们是时点副本、历史口径残留或调试产物，都不能用于统计，口径一律认 `dwd_item_review_di`。
