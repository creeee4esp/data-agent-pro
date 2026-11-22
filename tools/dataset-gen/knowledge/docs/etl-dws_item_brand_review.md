# 加工说明：dws_item_brand_review

## 加工逻辑

`dws_item_brand_review` 系列把商品评价明细按品牌粒度汇总，从 `dwd_item_review_di` 读数，产出日、周、月三张表。日表按 `review_date`、`brand_name` 分组，统计评价数 `COUNT(*)`、好评数 `SUM(is_good)` 和平均评分 `ROUND(AVG(rating), 2)`：

```sql
INSERT INTO dws_item_brand_review_1d
SELECT review_date, brand_name, COUNT(*), SUM(is_good), ROUND(AVG(rating), 2)
FROM dwd_item_review_di
GROUP BY review_date, brand_name
```

周表 `dws_item_brand_review_1w` 用 `DATE_SUB(review_date, INTERVAL WEEKDAY(review_date) DAY)` 把日期归到周一，月表 `dws_item_brand_review_1m` 用 `DATE_FORMAT(review_date, '%Y-%m-01')` 归到月初，其余聚合逻辑一致。

这里有两处容易误用：一是平均评分 `ROUND(AVG(rating), 2)` 不可加和，想算更大范围的平均分必须回到明细重新 `AVG`，不能把周表的日均分再平均；二是周表的日期是周一，不是自然周编号，跨表 JOIN 时对齐口径要一致。三张表当前分别约 3,905 行（日）、646 行（周）、156 行（月）。

## 依赖与调度

`dws_item_brand_review` 的上游是评价明细表 `dwd_item_review_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_item_brand_review` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。注意对账只针对评价数、好评数这类可加和指标，平均评分不参与对账。检查失败会导致下游品牌评价相关报表的评价数或好评率出现偏差。
