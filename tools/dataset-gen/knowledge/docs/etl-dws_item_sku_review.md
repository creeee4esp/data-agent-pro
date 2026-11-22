# 加工说明：dws_item_sku_review

## 加工逻辑

`dws_item_sku_review` 系列把评价明细按商品 SKU 粒度汇总，从 `dwd_item_review_di` 读数，产出日、周、月三张表。日表按 `review_date`、`sku_id` 分组，统计评价数 `COUNT(*)`、好评数 `SUM(is_good)` 和平均评分 `ROUND(AVG(rating), 2)`：

```sql
INSERT INTO dws_item_sku_review_1d
SELECT review_date, sku_id, COUNT(*), SUM(is_good), ROUND(AVG(rating), 2)
FROM dwd_item_review_di
GROUP BY review_date, sku_id
```

周表 `dws_item_sku_review_1w` 用 `DATE_SUB(review_date, INTERVAL WEEKDAY(review_date) DAY)` 把日期归到周一，月表 `dws_item_sku_review_1m` 用 `DATE_FORMAT(review_date, '%Y-%m-01')` 归到月初。

有一处容易误用：SKU 粒度表只有 `sku_id`，没有品牌或品类字段，想按品牌/品类看评价应直接查对应粒度的汇总表，不要拿本系列再去二次聚合推算品类口径。另外好评率要用好评数除以评价数现算，表里不存好评率，平均评分也不可跨周期加和。三张表当前分别约 10,179 行（日）、5,501 行（周）、2,699 行（月）。

## 依赖与调度

`dws_item_sku_review` 的上游是评价明细表 `dwd_item_review_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_item_sku_review` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账仅针对评价数、好评数等可加和指标。检查失败会导致下游 SKU 维度的评价报表评价数或好评率失真。
