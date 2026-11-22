# 取数需求单 REQ-2025-0083

## 需求

管理层要 2025-01 各一级品类按周的好评数 `good_cnt`（4星及以上），无筛选，用于年初品类口碑速览。

## 结论

`dws_item_cate1_review_1w` 对口，为一级品类粒度的按周评价汇总表。

- 字段：`stat_date`、`category1_id`、`category1_name`、`review_cnt`、`good_cnt`、`avg_rating`
- 时间范围：`stat_date` 限定 2025-01
- 可加：`good_cnt`、`review_cnt` 可跨行相加
- 提示：`avg_rating` 不可相加，跨品类需加权重算
- 交付：已交付，并沉淀为固定报表
