# 取数需求单 REQ-2025-0299

## 需求

商品部于 2025-12-29 提出：`avg_rating`（平均评分），一级品类评价月汇总，时间范围 2025-04，无筛选。

## 结论

`dws_item_cate1_review_1m` 为交付用表，物流数据组出数。字段：`stat_date`、`category1_id`、`category1_name`、`review_cnt`、`good_cnt`、`avg_rating`。

### 口径

`review_cnt`、`good_cnt` 可跨行相加；`avg_rating` 不可跨行相加，跨品类求整体评分须回评价明细加权重算。

### 交付

已交付，并沉淀为固定报表。
