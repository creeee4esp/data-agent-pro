# 取数需求单 REQ-2025-0145

## 需求

财务部 2025-06-18 提单：要 SKU 粒度评价数 `review_cnt`，按月汇总，区间 2025-02 至 2025-04，无筛选。

## 结论

`dws_item_sku_review_1m` 命中需求。取数字段：`stat_date`、`sku_id`、`review_cnt`、`good_cnt`、`avg_rating`；按 `stat_date` 卡 2025-02 至 2025-04。要点：`review_cnt`、`good_cnt` 可跨行相加，汇总无碍；`avg_rating` 是平均值，不能直接累加，跨期要看整体得按加权重算。本次已交付一次性取数结果。
