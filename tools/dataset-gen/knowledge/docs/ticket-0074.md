# 取数需求单 REQ-2025-0074

## 需求

市场部提出，需要 2025-03 单月、各一级品类的评价数 `review_cnt`，无筛选条件，用于月度品类口碑简报。

- 时间范围：2025-03
- 维度：一级品类
- 核心指标：评价数 `review_cnt`

## 结论

`dws_item_cate1_review_1m` 为一级品类粒度的按月评价汇总表，可直接满足需求。

- 取数字段：`stat_date`、`category1_id`、`category1_name`、`review_cnt`、`good_cnt`、`avg_rating`
- 可加性：`review_cnt`、`good_cnt` 可跨行相加，做全站或多品类汇总没有问题
- 注意：`avg_rating` 是平均值，不可相加；如需跨品类的总体评分，应回到评价明细重新加权，不能把各行的 `avg_rating` 直接求和或求简单平均
- 交付：已交付，并沉淀为固定报表供市场部每月查看
