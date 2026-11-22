# 指标说明：平均评分（avg_rating）

## 计算公式

平均评分的口径是 `ROUND(AVG(rating), 2)`，对明细行的评分取平均并保留两位小数。时间归属按评价日期 `review_date`。它是平均值类指标，不可加和：各天、各周的平均评分不能直接相加，也不能跨天简单取算术平均。举例：想要一周的平均评分，不能把 7 天的日均分再求平均，而要回到明细按一周重新算；想要月度平均评分，直接取月表最稳妥。

## 取哪张表

取数先按周期选后缀，日用 `_1d`、周用 `_1w`、月用 `_1m`，再按维度选表。

- 品牌维度：`dws_item_brand_review_1d` / `dws_item_brand_review_1w` / `dws_item_brand_review_1m`。
- 一级品类维度：`dws_item_cate1_review_1d` / `dws_item_cate1_review_1w` / `dws_item_cate1_review_1m`。
- SKU 维度：`dws_item_sku_review_1d` / `dws_item_sku_review_1w` / `dws_item_sku_review_1m`。

举个例子：要看某 SKU 昨天的平均评分，查 `dws_item_sku_review_1d`；要看某一级品类上月平均评分，查 `dws_item_cate1_review_1m` 或月度报表 `ads_item_review_score_monthly`。

## 来源与历史变化

平均评分来自评价明细表 `dwd_item_review_di`，各汇总表与报表都由它按维度聚合而来。历史变更方面，2025-06-01 物流数据组发布说明：月平均评分按评价数加权。也就是说，月度口径不是对各日平均分再平均，而是以评价数为权重加权，取月度数据时留意这一点。
