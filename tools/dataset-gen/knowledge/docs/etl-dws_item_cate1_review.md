# 加工说明：dws_item_cate1_review

## 加工逻辑

`dws_item_cate1_review` 系列将评价明细按一级品类粒度进行汇总，数据来源为 `dwd_item_review_di`，产出日、周、月三张表。日表 `dws_item_cate1_review_1d` 以 `review_date`、`category1_id`、`category1_name` 为分组键，统计评价数 `COUNT(*)`、好评数 `SUM(is_good)` 与平均评分 `ROUND(AVG(rating), 2)`：

```sql
INSERT INTO dws_item_cate1_review_1d
SELECT review_date, category1_id, category1_name,
       COUNT(*), SUM(is_good), ROUND(AVG(rating), 2)
FROM dwd_item_review_di
GROUP BY review_date, category1_id, category1_name
```

周表 `dws_item_cate1_review_1w` 通过 `DATE_SUB(review_date, INTERVAL WEEKDAY(review_date) DAY)` 将日期归并至所在周的周一，月表 `dws_item_cate1_review_1m` 通过 `DATE_FORMAT(review_date, '%Y-%m-01')` 归并至月初，聚合口径与日表保持一致。三张表当前分别约 2,502 行（日）、378 行（周）、91 行（月）。其中平均评分 `ROUND(AVG(rating), 2)` 为不可加和指标，更大周期的平均评分须回源明细重新计算。

## 依赖与调度

`dws_item_cate1_review` 的上游为评价明细表 `dwd_item_review_di`。调度策略为每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_item_cate1_review` 产出后须通过四项质量检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。其中对账仅适用于评价数、好评数等可加和指标。任一检查未通过，将导致下游一级品类维度的评价报表在评价数量或好评率上产生偏差。
