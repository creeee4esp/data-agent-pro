# 表说明：ads_item_review_score_monthly（ADS-一级品类月度评分）

## 概述

`ads_item_review_score_monthly` 记录的是各一级品类每月的评价情况，属于 ADS 层，表注释是 ADS-一级品类月度评分，目前有 91 行。它由评价明细表 `dwd_item_review_di` 按评价月份和一级品类聚合而来，想看某个品类的口碑走势、哪个品类评分高、好评率如何，用它就够了。要提醒新同事一句：这张表的粒度是一级品类加月份，拿它去看单个商品或品牌的评分是看不到的。

## 字段

- `stat_month` 是 char(7) 的统计月份，表示评价发生的月份，分析时以此作为时间轴。
- `category1_name` 是 varchar(50) 的一级品类名称，是本表除月份外的唯一拆分维度，没有更细的二级品类或 SKU。
- `review_cnt` 是 int 的评价数，表示当月该品类收到的评价条数，评分类指标都应结合它看样本量。
- `avg_rating` 是 decimal(4,2) 的平均评分，是按评价条数算出来的均值，跨月比较时不要把多个月的 `avg_rating` 直接再平均，样本量不同会失真。
- `good_rate` 是 decimal(8,4) 的好评率，已经算好直接用即可，同样不建议跨月简单平均。

## 上下游

`ads_item_review_score_monthly` 的唯一上游是 `dwd_item_review_di`，也就是评价明细宽表，加工时按评价日期的月份和一级品类分组，统计评价数、平均评分和好评率。它没有下游表，是面向分析和报表的末端结果。

## 使用注意

`ads_item_review_score_monthly` 适合做一级品类层面的月度口碑分析和品类间横向对比，直接取数即可。它不适合下钻到品牌、二级品类或单品，也不适合把多个月的平均值简单再平均。另外这张表周边有不少同家族的噪声表：一个备份表 `ads_item_review_score_monthly_bak`，若干带日期的备份如 `ads_item_review_score_monthly_bak_20250221`，还有一个测试表 `test_ads_item_review_score_monthly`。备份是某个时点的留存副本，测试表是开发调试用的临时产物，二者都不保证数据完整和最新，千万不要拿来做统计，正式口径只认 `ads_item_review_score_monthly`。
