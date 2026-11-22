# 取数需求单 REQ-2025-0262

## 需求

市场部于 2025-11-04 提出取数，需要 2025-11 的平均评分（`avg_rating`），口径为品牌粒度评价月汇总，无筛选条件。编号 REQ-2025-0262，处理人流量数据组。

## 结论

`dws_item_brand_review_1m`（DWS-品牌粒度评价汇总（月））为本次用表，取 `stat_date`、`brand_name`、`review_cnt`、`good_cnt`、`avg_rating`，报告期 `stat_date` 取 2025-11。

加和口径：`avg_rating` 为平均值，不可跨行相加，求整体平均评分须以 `review_cnt` 加权；`review_cnt`、`good_cnt` 可跨行相加，用于统计评价数与好评数。

本次已交付一次性取数结果。
