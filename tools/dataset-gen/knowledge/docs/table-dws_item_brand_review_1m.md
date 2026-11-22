# 表说明：dws_item_brand_review_1m（DWS-品牌粒度评价汇总（月））

## 概述

`dws_item_brand_review_1m` 是品牌维度的月度评价汇总，属于 DWS 层，表注释为 DWS-品牌粒度评价汇总（月），当前 156 行。粒度是统计月 × 品牌，由评价明细 `dwd_item_review_di` 按月聚合而来。要先点出一处易错点：`stat_date` 存的是月初日期而不是 char(7) 的月份字符串，筛月份时要按日期匹配月初，别用形如 2025-06 的字符串去比。

## 字段

- `stat_date` 是 date 的统计月，用月初日期表示该月，如 2025-06-01。
- `brand_name` 是 varchar(50) 的品牌，是除月份外的拆分维度。
- `review_cnt` 是 int 的评价数，可跨月相加。
- `good_cnt` 是 int 的好评数（4星及以上），可跨月相加。
- `avg_rating` 是 decimal(4,2) 的平均评分，是月内均值，跨月不要直接再平均。

## 上下游

`dws_item_brand_review_1m` 的唯一上游是 `dwd_item_review_di`，加工时把评价日期归到月初并按品牌分组。它没有下游表，供分析按需使用。

## 使用注意

`dws_item_brand_review_1m` 适合看品牌的月度评价数、好评数和平均评分走势。要算跨月整体好评率，应分别累加 `good_cnt` 与 `review_cnt` 再相除，不要平均月度 `avg_rating`。它不支持下钻到 SKU 或品类。本家族有带日期备份 `dws_item_brand_review_1m_bak_20250204` 等和测试表 `test_dws_item_brand_review_1m`，均为时点副本或调试产物，不保证完整，统计一律用 `dws_item_brand_review_1m`。
