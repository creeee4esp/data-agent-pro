# 表说明：dws_item_cate1_review_1w（DWS-一级品类粒度评价汇总（周））

## 概述

`dws_item_cate1_review_1w` 是一级品类维度的周度评价汇总，属于 DWS 层，表注释为 DWS-一级品类粒度评价汇总（周），当前 378 行。它的粒度是统计周加一级品类，由评价明细 `dwd_item_review_di` 按自然周聚合，周以周一为起点。看某个品类按周的口碑波动，它比按日表更平滑、比按月表更细。

## 字段

- `stat_date` 是 date 的统计周，用该周的周一日期表示。
- `category1_id` 是 int 的一级品类ID，关联时建议用 ID。
- `category1_name` 是 varchar(50) 的一级品类名称，便于展示。
- `review_cnt` 是 int 的评价数，可跨周相加。
- `good_cnt` 是 int 的好评数（4星及以上），可跨周相加。
- `avg_rating` 是 decimal(4,2) 的平均评分，是周内均值，跨周别直接再平均。

## 上下游

`dws_item_cate1_review_1w` 的唯一上游是 `dwd_item_review_di`，加工时把评价日期对齐到所在周的周一，按一级品类 ID 和名称分组汇总。它没有下游表，供分析按需使用。

## 使用注意

`dws_item_cate1_review_1w` 适合做一级品类的周度评价趋势分析。算区间整体好评率要用 `SUM(good_cnt) / SUM(review_cnt)`，不要平均 `avg_rating`。它不支持下钻到品牌或 SKU。本家族有历史归档表 `dws_item_cate1_review_1w_his`、备份表 `dws_item_cate1_review_1w_bak` 和带日期备份 `dws_item_cate1_review_1w_bak_20250820` 等，分别是旧快照和时点副本，都不保证完整，统计一律用 `dws_item_cate1_review_1w`。
