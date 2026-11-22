# 表说明：dws_item_brand_review_1d（DWS-品牌粒度评价汇总（日））

## 概述

`dws_item_brand_review_1d` 按品牌和日汇总评价，属 DWS 层，表注释为 DWS-品牌粒度评价汇总（日），当前 3,905 行。粒度为统计日期 × 品牌。上游为 `dwd_item_review_di`。用于看品牌每日口碑。

## 字段

- `stat_date`：date，统计日期，日粒度。
- `brand_name`：varchar(50)，品牌，除日期外的拆分维度。
- `review_cnt`：int，评价数，可按日期区间相加。
- `good_cnt`：int，好评数（4星及以上），可相加。
- `avg_rating`：decimal(4,2)，平均评分，为日内均值，不能跨日直接再平均。

## 上下游

`dws_item_brand_review_1d` 数据来自 `dwd_item_review_di`，按评价日期和品牌分组统计。无下游表，供分析按需使用。

## 使用注意

- 适合：看品牌每日评价数、好评数、平均评分。
- 算区间好评率：分别汇总 `good_cnt` 与 `review_cnt` 再相除，勿平均 `avg_rating`。
- 跨周期统计：可用同家族周表、月表，避免手工按日累加平均评分。
- 噪声表：带日期备份 `dws_item_brand_review_1d_bak_20250201` 等、测试表 `test_dws_item_brand_review_1d`，为时点副本或调试产物，不能用于统计。
- 口径：统计一律用 `dws_item_brand_review_1d`。
