# 取数需求单 REQ-2025-0089

## 需求

市场部需 2025-02 至 2025-04、各品牌按月的评价数 `review_cnt`，无筛选，用于品牌口碑声量的季度跟踪。

- 时间范围：2025-02 至 2025-04
- 维度：品牌
- 核心指标：评价数 `review_cnt`

## 结论

`dws_item_brand_review_1m` 对口，为品牌粒度的按月评价汇总表。

- 取数字段：`stat_date`、`brand_name`、`review_cnt`、`good_cnt`、`avg_rating`
- 时间范围：`stat_date` 限定 2025-02 至 2025-04
- 口径：`review_cnt`、`good_cnt` 可跨行相加，跨月或多品牌汇总均可
- 注意：`avg_rating` 不可相加，跨行比较只能逐行看，总体评分须回明细加权
- 交付：已交付，附 SQL 供需求方自助复用
