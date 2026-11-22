# 加工说明：ods_item_review_di

## 加工逻辑

`ods_item_review_di` 存 ODS-商品评价表，当前 16,845 行，是业务源表 `item_review` 的贴源落地。加工整表原样同步，不清洗不聚合，只在源表全部字段后追加固定 ETL 时间列和分区列 `dt`。

```sql
INSERT INTO ods_item_review_di SELECT item_review.*, <固定 etl_time>, <分区 dt> FROM item_review
```

有个容易误用的地方要点一下：这是贴源表，评分、好评等口径都还没加工。想按一级品类看评分，应该用下游 `dwd_item_review_di`，那里才关联了 `dim_sku` 补上了品牌和品类；直接在这张表上算品类评分会关联不到品类字段。

## 依赖与调度

`ods_item_review_di` 依赖一张上游表，业务源表 `item_review`。调度每日 01:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。作为 ODS 层任务，它跑在下游各层前面。

## 质量检查

`ods_item_review_di` 发布前跑三项检查：主键 / 粒度唯一，保证评价不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查失败会往下游传。评价重复会让 `dwd_item_review_di` 的评价条数和好评率翻倍；关键字段为空会让下游评分口径缺失；行数骤降往往是源表 `item_review` 没抽全，评价规模被低估。看到告警先核查源表再发布。
