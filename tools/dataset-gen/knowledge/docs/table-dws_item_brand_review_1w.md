# 表说明：dws_item_brand_review_1w（DWS-品牌粒度评价汇总（周））

## 概述

`dws_item_brand_review_1w` 是品牌维度的周度评价汇总，属于 DWS 层，表注释为 DWS-品牌粒度评价汇总（周），当前 646 行。粒度是统计周 × 品牌，由评价明细 `dwd_item_review_di` 按自然周聚合，周以周一为起点。看某品牌按周的口碑波动，用它最合适，比如 `SELECT stat_date, review_cnt, avg_rating FROM dws_item_brand_review_1w WHERE brand_name = '某品牌' ORDER BY stat_date`。

## 字段

- `stat_date` 是 date 的统计周，用该周的周一日期表示。
- `brand_name` 是 varchar(50) 的品牌，是除周外的拆分维度。
- `review_cnt` 是 int 的评价数，可跨周相加。
- `good_cnt` 是 int 的好评数（4星及以上），可跨周相加。
- `avg_rating` 是 decimal(4,2) 的平均评分，是周内均值，跨周别直接再平均。

## 上下游

`dws_item_brand_review_1w` 的唯一上游是 `dwd_item_review_di`，加工时把评价日期对齐到所在周的周一并按品牌分组。它没有下游表，供分析按需使用。

## 使用注意

`dws_item_brand_review_1w` 适合看品牌的周度评价趋势和好评情况。算区间好评率要用 `SUM(good_cnt) / SUM(review_cnt)`，不要平均 `avg_rating`。它不支持下钻到 SKU 或品类。本家族有带日期备份 `dws_item_brand_review_1w_bak_20250128` 等、旧版本表 `dws_item_brand_review_1w_old` 和测试表 `test_dws_item_brand_review_1w`，均为时点副本、历史残留或调试产物，不能用于统计，口径一律用 `dws_item_brand_review_1w`。
