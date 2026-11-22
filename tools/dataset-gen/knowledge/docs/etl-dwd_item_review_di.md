# 加工说明：dwd_item_review_di

## 加工逻辑

`dwd_item_review_di` 存的是 DWD-评价明细，一条评价一行，当前 16,845 行。加工把商品评价表 `ods_item_review_di` 和商品维表 `dim_sku` 按 `sku_id` 关联，给每条评价补上品牌和一级品类，方便下游直接按品类看评分。

字段上，取评价本身的 `review_id`、`sku_id`、`user_id`、`rating`，用 `IF(r.rating >= 4, 1, 0)` 派生好评标记（评分不低于 4 记 1），从 `dim_sku` 带出 `brand_name`、`category1_id`、`category1_name`，再用 `DATE(r.review_time)` 取评价日期，分区 `dt` 沿用上游。

```sql
SELECT r.review_id, r.sku_id, r.user_id, r.rating, IF(r.rating >= 4, 1, 0),
  d.brand_name, d.category1_id, d.category1_name, DATE(r.review_time), r.dt
FROM ods_item_review_di r JOIN dim_sku d ON d.sku_id = r.sku_id
```

举个取数例子：要统计"某一级品类的好评率"，直接按 `category1_name` 分组、对好评标记求和再除以评价条数即可，不用回 `dim_sku` 再关联品类。

## 依赖与调度

`dwd_item_review_di` 依赖两张上游表：商品维表 `dim_sku` 和商品评价表 `ods_item_review_di`。调度每日 02:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dwd_item_review_di` 发布前跑三项检查：主键 / 粒度唯一，保证 `review_id` 不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查失败会直接影响下游。`review_id` 重复会让评价条数和好评率被重复计算；品类字段为空会让按品类汇总的评分报表丢分组；行数骤降往往是 `ods_item_review_di` 没抽全，下游评分会失真。看到告警先定位上游再发布。
