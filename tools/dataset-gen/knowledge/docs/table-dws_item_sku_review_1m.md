# 表说明：dws_item_sku_review_1m（DWS-商品SKU 粒度评价汇总（月））

## 概述

`dws_item_sku_review_1m` 为 SKU 粒度的月度评价汇总，归属 DWS 层，表注释为 DWS-商品SKU 粒度评价汇总（月），当前共 2,699 行。其粒度为统计月与 SKU，由评价明细 `dwd_item_review_di` 按月聚合而成，用于单品的月度口碑分析。使用时应注意，`stat_date` 以月初日期表示统计月，而非月份字符串。

## 字段

- `stat_date` 为 date 类型的统计月，以月初日期表示该月，如 2025-06-01。
- `sku_id` 为 bigint 类型的商品SKU ID，是除月份外的拆分维度。
- `review_cnt` 为 int 类型的评价数，可跨月累加。
- `good_cnt` 为 int 类型的好评数，口径为 4星及以上，可跨月累加。
- `avg_rating` 为 decimal(4,2) 类型的平均评分，为月内均值，不可跨月直接再平均。

## 上下游

`dws_item_sku_review_1m` 的唯一上游为 `dwd_item_review_di`，加工时将评价日期归至月初并按 SKU 分组汇总。该表无下游表，供分析按需使用。

## 使用注意

`dws_item_sku_review_1m` 适用于单品的月度评价量、评分走势分析与单品间对比。计算跨月整体好评率时，应分别累加 `good_cnt` 与 `review_cnt` 后相除，不得对月度 `avg_rating` 求平均。该表不支持按品牌、品类聚合。本家族存在备份表 `dws_item_sku_review_1m_bak`、带日期备份 `dws_item_sku_review_1m_bak_20251216`、旧版本表 `dws_item_sku_review_1m_v1` 与测试表 `test_dws_item_sku_review_1m`，分别为时点副本、历史口径残留与调试产物，均不得用于统计，口径一律以 `dws_item_sku_review_1m` 为准。
