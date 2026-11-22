# 取数需求单 REQ-2025-0133

## 需求

财务部需 2025-01、各品牌按日的平均评分 `avg_rating`，无筛选，用于品牌口碑的日度速览。

- 时间范围：2025-01
- 维度：品牌
- 核心指标：平均评分 `avg_rating`

## 结论

`dws_item_brand_review_1d` 对口，为品牌粒度的按日评价汇总表。

- 取数字段：`stat_date`、`brand_name`、`review_cnt`、`good_cnt`、`avg_rating`
- 时间范围：`stat_date` 限定 2025-01
- 关键口径：`avg_rating` 不可跨行相加，跨日或跨品牌不能直接平均，须用 `review_cnt` 加权
- 可加：`review_cnt`、`good_cnt` 可跨行相加
- 交付：已交付一次性取数结果
