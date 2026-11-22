# 取数需求单 REQ-2025-0193

## 需求

财务部于 2025-08-18 提单，要点如下：

- 对象：品牌粒度的平均评分 `avg_rating`
- 周期：按月汇总
- 报告期：2025-02
- 筛选：无

## 结论

`dws_item_brand_review_1m` 可满足本需求，要点如下：

- 取数字段：`stat_date`、`brand_name`、`review_cnt`、`good_cnt`、`avg_rating`，按 `stat_date` 限定 2025-02
- 关键口径：`avg_rating` 为平均评分，不能跨行直接相加，整体平均须按评价数加权重算
- 可加和项：`review_cnt` 与 `good_cnt` 可直接相加
- 交付：已交付，附 SQL 供财务部自助复用
