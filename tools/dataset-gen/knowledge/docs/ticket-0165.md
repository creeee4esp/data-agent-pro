# 取数需求单 REQ-2025-0165

## 需求

客服部 2025-07-14 提单，想看 SKU 维度的评价数 `review_cnt`，按周汇总，报告期 2025-04，不加筛选。他们想按周盯一下重点 SKU 的评价量变化，后续可能还会反复拉数。

## 结论

`dws_item_sku_review_1w` 这张表对口，取数用到 `stat_date`、`sku_id`、`review_cnt`、`good_cnt`、`avg_rating` 这几个字段，按 `stat_date` 卡 2025-04 就行。提醒一下：`review_cnt` 和 `good_cnt` 可以跨行相加，按 SKU 或按周汇总都没问题；但 `avg_rating` 是平均评分，不能直接累加，跨期看整体得按评价数加权重算。本次已交付，并附了 SQL 方便客服部自助复用。
