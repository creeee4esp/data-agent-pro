# 表说明：dws_item_sku_review_1d（DWS-商品SKU 粒度评价汇总（日））

## 概述

`dws_item_sku_review_1d` 为 SKU 粒度的每日评价汇总，归属 DWS 层，表注释为 DWS-商品SKU 粒度评价汇总（日），当前共 10,179 行。其粒度为统计日期与 SKU，由评价明细 `dwd_item_review_di` 按日和 SKU 聚合而成，用于单品的每日口碑监控。使用时应注意，该表为 SKU 级汇总，不含品牌与品类维度。

## 字段

- `stat_date` 为 date 类型的统计日期，是日粒度的时间轴。
- `sku_id` 为 bigint 类型的商品SKU ID，是除日期外的拆分维度。
- `review_cnt` 为 int 类型的评价数，可按日期区间累加。
- `good_cnt` 为 int 类型的好评数，口径为 4星及以上，可累加。
- `avg_rating` 为 decimal(4,2) 类型的平均评分，为当日均值，不可跨日直接再平均。

## 上下游

`dws_item_sku_review_1d` 的唯一上游为 `dwd_item_review_di`，加工时按评价日期与 SKU 分组，统计评价数、好评数与平均评分。该表无下游表，供分析按需使用。

## 使用注意

`dws_item_sku_review_1d` 适用于单品每日评价量与评分的监控及单品间对比。计算区间整体好评率时，应分别累加 `good_cnt` 与 `review_cnt` 后相除，不得对日度 `avg_rating` 求平均。该表不支持按品牌、品类聚合，跨周、跨月统计建议使用同家族的周表、月表。本家族存在带日期备份 `dws_item_sku_review_1d_bak_20250125` 等，为特定时点的留存副本，未必完整或最新，不得用于统计，口径一律以 `dws_item_sku_review_1d` 为准。
