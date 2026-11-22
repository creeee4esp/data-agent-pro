# 加工说明：ads_item_review_score_monthly

## 加工逻辑

`ads_item_review_score_monthly` 存 ADS-一级品类月度评分，按"评价月份 + 一级品类"汇总，当前 91 行。

要点：

- 数据源是评价明细 `dwd_item_review_di`，单表加工，不做关联。
- 用 `DATE_FORMAT(review_date, '%Y-%m')` 把评价日期切成月份。
- 按月份和 `category1_name` 分组。
- 评价条数用 `COUNT(*)`。
- 平均评分用 `ROUND(AVG(rating), 2)`，保留两位小数。
- 好评率用 `ROUND(SUM(is_good) / COUNT(*), 4)`，保留四位小数。

```sql
SELECT DATE_FORMAT(review_date, '%Y-%m'), category1_name, COUNT(*),
  ROUND(AVG(rating), 2), ROUND(SUM(is_good) / COUNT(*), 4) FROM dwd_item_review_di
GROUP BY DATE_FORMAT(review_date, '%Y-%m'), category1_name
```

## 依赖与调度

`ads_item_review_score_monthly` 依赖一张上游表。

- 上游表：评价明细 `dwd_item_review_di`。
- 调度：每日 05:00 开始。
- 触发：依赖上游全部成功后运行。
- 口径：处理前一天（T-1）的数据。

## 质量检查

`ads_item_review_score_monthly` 发布前跑四项检查。

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。
- 可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

失败影响：

- 粒度重复，一级品类的评价条数和好评率会被重复计算，评分失真。
- 行数骤降，说明 `dwd_item_review_di` 没抽全，下游会低估评价规模。
- 对账不平，平均评分和好评率不可信，不应发布。
