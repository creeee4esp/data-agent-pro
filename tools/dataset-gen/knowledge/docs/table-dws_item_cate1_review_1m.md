# 表说明：dws_item_cate1_review_1m（DWS-一级品类粒度评价汇总（月））

## 概述

`dws_item_cate1_review_1m` 是一级品类维度的月度评价汇总，属于 DWS 层，表注释为 DWS-一级品类粒度评价汇总（月），当前 91 行。它的粒度是统计月加一级品类，由评价明细 `dwd_item_review_di` 按月聚合而来。想看某个一级品类的月度口碑走势、各品类评分横比，用它比查明细省事。要提醒新同事的是，这里的 `stat_date` 用月初日期表示一个月，不是 char(7) 的月份串。

## 字段

- `stat_date` 是 date 的统计月，用月初日期表示该月，如 2025-06-01。
- `category1_id` 是 int 的一级品类ID，关联时建议用 ID。
- `category1_name` 是 varchar(50) 的一级品类名称，便于展示。
- `review_cnt` 是 int 的评价数，可跨月相加。
- `good_cnt` 是 int 的好评数（4星及以上），可跨月相加。
- `avg_rating` 是 decimal(4,2) 的平均评分，是月内均值，跨月别直接再平均。

## 上下游

`dws_item_cate1_review_1m` 的唯一上游是 `dwd_item_review_di`，加工时把评价日期归到月初，按一级品类 ID 和名称分组汇总。它没有下游表，供分析按需使用。

## 使用注意

`dws_item_cate1_review_1m` 适合做一级品类的月度口碑分析和品类间对比。算跨月整体好评率要用 `SUM(good_cnt) / SUM(review_cnt)`，不要平均月度 `avg_rating`。它不支持下钻到品牌或 SKU。本家族有备份表 `dws_item_cate1_review_1m_bak` 和带日期备份 `dws_item_cate1_review_1m_bak_20250629` 等，都是时点副本，不保证完整，统计一律用 `dws_item_cate1_review_1m`。
