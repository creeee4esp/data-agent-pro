# 表说明：dws_item_sku_review_1w（DWS-商品SKU 粒度评价汇总（周））

## 概述

`dws_item_sku_review_1w` 是 SKU 粒度的周度评价汇总，属于 DWS 层，表注释为 DWS-商品SKU 粒度评价汇总（周），当前 5,501 行。粒度是统计周加 SKU，由评价明细 `dwd_item_review_di` 按自然周聚合，周以周一为起点。看某个单品按周的评价量和评分走势，直接用它，比如 `SELECT stat_date, review_cnt, avg_rating FROM dws_item_sku_review_1w WHERE sku_id = 123 ORDER BY stat_date`。

## 字段

- `stat_date` 是 date 的统计周，用该周的周一日期表示。
- `sku_id` 是 bigint 的商品SKU ID，是除周外的拆分维度。
- `review_cnt` 是 int 的评价数，可跨周相加。
- `good_cnt` 是 int 的好评数（4星及以上），可跨周相加。
- `avg_rating` 是 decimal(4,2) 的平均评分，是周内均值，跨周别直接再平均。

## 上下游

`dws_item_sku_review_1w` 的唯一上游是 `dwd_item_review_di`，加工时把评价日期对齐到所在周的周一并按 SKU 分组汇总。它没有下游表，供分析按需使用。

## 使用注意

`dws_item_sku_review_1w` 适合做单品的周度评价趋势分析和单品对比。算区间整体好评率要用 `SUM(good_cnt) / SUM(review_cnt)`，不要平均 `avg_rating`。它不支持按品牌或品类聚合。本家族有备份表 `dws_item_sku_review_1w_bak`、带日期备份 `dws_item_sku_review_1w_bak_20250629`、旧版本表 `dws_item_sku_review_1w_v1` 和测试表 `test_dws_item_sku_review_1w`，分别是时点副本、历史残留和调试产物，不能用于统计，口径一律用 `dws_item_sku_review_1w`。
