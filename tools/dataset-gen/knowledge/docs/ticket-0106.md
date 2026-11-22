# 取数需求单 REQ-2025-0106

## 需求

财务部想要 2025-02 单月、按商品 SKU 粒度的月度平均评分 `avg_rating`，不加筛选，用来结合口碑看看哪些单品满意度偏低、可能影响后续退换成本。

## 结论

`dws_item_sku_review_1m` 是商品 SKU 粒度的按月评价汇总表，拿它来做正合适。字段取 `stat_date`、`sku_id`、`review_cnt`、`good_cnt`、`avg_rating`，按 `stat_date` 限定在 2025-02。

要特别和财务部说清楚：平均评分 `avg_rating` 不能跨行相加，也不能把多个 SKU 的评分直接求简单平均，那样会忽略各 SKU 评价量的差异。如果想看一批 SKU 的整体评分，得用评价数 `review_cnt` 作权重重新加权。同表里的 `review_cnt`、`good_cnt` 是可以相加的。

这次已经交付，并沉淀成固定报表，财务部每月可以直接查。
