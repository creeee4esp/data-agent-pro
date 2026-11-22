# 指标说明：好评数（4星及以上）（good_cnt）

## 计算公式

`good_cnt` 统计的是 4 星及以上的好评条数，口径是 `SUM(is_good)`。理解起来很直接：每条评价上有一个 `is_good` 标记，评分达到 4 星及以上记 1，否则记 0，把这些标记加起来就是好评数。时间归属看评价日期 `review_date`，哪天评价就归到哪天，和评价数保持一致，这样好评数和评价总数在同一张表里能直接对照着看。该指标可加和，各天、各周的结果能直接相加，日粒度求和即得更长周期的好评数。

## 取哪张表

按统计周期和维度选表是第一步，日粒度用 `_1d`，周粒度用 `_1w`，月粒度用 `_1m`。

- 品牌维度：日用 `dws_item_brand_review_1d`，周用 `dws_item_brand_review_1w`，月用 `dws_item_brand_review_1m`。
- 一级品类维度：日用 `dws_item_cate1_review_1d`，周用 `dws_item_cate1_review_1w`，月用 `dws_item_cate1_review_1m`。
- SKU 维度：日用 `dws_item_sku_review_1d`，周用 `dws_item_sku_review_1w`，月用 `dws_item_sku_review_1m`。

想算好评率时，在同一张表里拿 `good_cnt` 和评价数相除就行，粒度天然对齐。

## 来源与历史变化

数据一路追到明细就是评价明细表 `dwd_item_review_di`，上面各 `dws` 表都是在它之上按不同维度聚合出来的。这个指标自上线以来未调整计算规则，`is_good` 的打标口径一直没变，历史数据和当前数据可以放心做趋势对比。
