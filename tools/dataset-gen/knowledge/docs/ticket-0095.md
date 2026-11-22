# 取数需求单 REQ-2025-0095

## 需求

会员中心需 2025-01 至 2025-03、各一级品类按月的平均评分 `avg_rating`，无筛选，用于品类满意度的季度观察。

- 时间范围：2025-01 至 2025-03
- 维度：一级品类
- 核心指标：平均评分 `avg_rating`

## 结论

`dws_item_cate1_review_1m` 对口，为一级品类粒度的按月评价汇总表。

- 取数字段：`stat_date`、`category1_id`、`category1_name`、`review_cnt`、`good_cnt`、`avg_rating`
- 时间范围：`stat_date` 限定 2025-01 至 2025-03
- 关键口径：`avg_rating` 不可跨行相加，跨月或跨品类不能直接平均，须用 `review_cnt` 作权重重新加权
- 可加：`review_cnt`、`good_cnt` 可跨行相加
- 交付：已交付一次性取数结果
