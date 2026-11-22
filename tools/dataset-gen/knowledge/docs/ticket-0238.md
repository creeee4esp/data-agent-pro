# 取数需求单 REQ-2025-0238

## 需求

管理层于 2025-10-10 提出取数，要点如下：

- 指标：平均评分（`avg_rating`）
- 口径：一级品类粒度评价月汇总
- 时间范围：2025-05 至 2025-07
- 筛选条件：无
- 单据编号：REQ-2025-0238，处理人商品数据组

## 结论

`dws_item_cate1_review_1m`（DWS-一级品类粒度评价汇总（月））为本次用表，要点如下：

- 取用字段：`stat_date`、`category1_id`、`category1_name`、`review_cnt`、`good_cnt`、`avg_rating`
- 报告期：`stat_date` 覆盖 2025-05 至 2025-07
- 关键提醒：`avg_rating` 为平均值，不可跨行相加，求整体平均评分须以 `review_cnt` 加权，不能简单求和或取算术平均
- 可加字段：`review_cnt`、`good_cnt` 可跨行相加
- 交付情况：已交付，附 SQL 供需求方自助复用
