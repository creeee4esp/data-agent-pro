# 取数需求单 REQ-2025-0280

## 需求

物流部于 2025-11-30 提出取数，需要 2025-03 至 2025-05 的好评数（`good_cnt`），口径为品牌粒度评价月汇总，无筛选条件。编号 REQ-2025-0280，处理人用户数据组。

## 结论

`dws_item_brand_review_1m`（DWS-品牌粒度评价汇总（月））为本次用表，取 `stat_date`、`brand_name`、`review_cnt`、`good_cnt`、`avg_rating`，报告期 `stat_date` 覆盖 2025-03 至 2025-05。

加和口径：`good_cnt`、`review_cnt` 可跨行相加，汇总好评数与评价数用这两个字段；`avg_rating` 为平均值，不可跨行相加，如需整体平均评分应以 `review_cnt` 加权。

本次已交付一次性取数结果。
