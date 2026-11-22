# 取数需求单 REQ-2025-0207

## 需求

物流部 2025-08-29 提单，要一级品类维度的评价数 `review_cnt`，按周汇总，报告期 2025-04，不加筛选。想按周看一下各一级品类的评价量。

## 结论

`dws_item_cate1_review_1w` 这张表对口，取数用到 `stat_date`、`category1_id`、`category1_name`、`review_cnt`、`good_cnt`、`avg_rating` 这几个字段，按 `stat_date` 卡 2025-04 就行。提醒一下：`review_cnt` 和 `good_cnt` 可以跨行相加，按品类或按周汇总都没问题；但 `avg_rating` 是平均评分，不能直接累加，跨期看整体得按评价数加权重算。本次已交付一次性取数结果。
