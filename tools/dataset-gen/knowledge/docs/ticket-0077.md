# 取数需求单 REQ-2025-0077

## 需求

运营部需 2025-02 各一级品类按日好评数 `good_cnt`（4星及以上），无筛选，用于品类口碑日度跟踪。

## 结论

`dws_item_cate1_review_1d` 覆盖本次需求：一级品类粒度、按日评价汇总。

- 字段：`stat_date`、`category1_id`、`category1_name`、`review_cnt`、`good_cnt`、`avg_rating`
- 时间范围：`stat_date` 限定 2025-02
- 口径：`good_cnt`、`review_cnt` 可跨行相加，按品类或跨天汇总均可
- 提示：`avg_rating` 为平均评分，不可相加，需跨行比较时只能逐行看
- 交付：已交付，附 SQL 供需求方自助复用
