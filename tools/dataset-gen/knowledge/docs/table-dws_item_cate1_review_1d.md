# 表说明：dws_item_cate1_review_1d（DWS-一级品类粒度评价汇总（日））

## 概述

`dws_item_cate1_review_1d` 是一级品类维度的每日评价汇总，属于 DWS 层，表注释为 DWS-一级品类粒度评价汇总（日），当前 2,502 行。它的粒度是统计日期加一级品类，由评价明细 `dwd_item_review_di` 按日和品类聚合而来。想看某个一级品类每天的评价量和口碑，这张表最合适，相比直接查明细省去了聚合的工夫。

## 字段

- `stat_date` 是 date 的统计日期，是日粒度的时间轴。
- `category1_id` 是 int 的一级品类ID，和名称一起作为维度，关联时建议用 ID。
- `category1_name` 是 varchar(50) 的一级品类名称，便于直接展示和阅读。
- `review_cnt` 是 int 的评价数，可按日期区间相加。
- `good_cnt` 是 int 的好评数，统计口径是 4星及以上，可相加。
- `avg_rating` 是 decimal(4,2) 的平均评分，是当日均值，跨日分析时不要把多天的 `avg_rating` 直接再平均。

## 上下游

`dws_item_cate1_review_1d` 的唯一上游是 `dwd_item_review_di`，加工时按评价日期、一级品类 ID 和名称分组，统计评价数、好评数和平均评分。它没有下游表，供分析按需使用。

## 使用注意

`dws_item_cate1_review_1d` 适合做一级品类的每日评价趋势分析和品类间横向对比。要算一段时间的整体好评率，应分别累加 `good_cnt` 与 `review_cnt` 再相除，而不是平均日度 `avg_rating`。它不支持下钻到品牌或 SKU，跨周跨月建议改用同家族的周表和月表。本家族有备份表 `dws_item_cate1_review_1d_bak` 和一批带日期备份如 `dws_item_cate1_review_1d_bak_20250218`，它们是某时点的留存副本，不随任务更新、不保证完整，统计一律用 `dws_item_cate1_review_1d`。
