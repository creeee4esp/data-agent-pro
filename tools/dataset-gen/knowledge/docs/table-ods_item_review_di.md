# 表说明：ods_item_review_di（ODS-商品评价表）

## 概述

`ods_item_review_di` 是 ODS 层的商品评价贴源表，原样承接评价明细，当前 16,845 行，一行一条评价。它比业务侧多了 `etl_time` 和 `dt` 两个技术字段，是评分、带图率等分析往上加工的起点。举个例子，想看全量评价的平均分，`SELECT AVG(rating) FROM ods_item_review_di WHERE dt = '某分区'`，加上 `dt` 过滤能少扫不少数据。

## 字段

- `review_id`（bigint）：评价 ID，单条评价的唯一标识。
- `order_item_id`（bigint）：订单明细 ID，想追到哪笔明细的评价用它。
- `sku_id`（bigint）：商品 SKU ID，按商品聚合评价用它，比如按 `sku_id` 算各商品平均分。
- `user_id`（bigint）：评价用户 ID，按用户看评价行为用它。
- `rating`（tinyint）：评分，1～5 星，算好评率要先定阈值，例如把 `rating >= 4` 记为好评。
- `has_image`（tinyint）：是否带图，1-是、0-否，算带图率就 `SUM(has_image) / COUNT(*)`。
- `review_time`（datetime）：评价时间，晚于签收时间，按时间统计留意滞后性。
- `etl_time`（datetime）：ETL 同步时间，技术字段，不是业务时间，别拿它做口径。
- `dt`（date）：分区日期，查询时用它裁剪分区。

## 上下游

`ods_item_review_di` 的上游是 `item_review`，通过 ETL 同步落到 ODS 层。它的下游是 `dwd_item_review_di`，由后者承接做明细层加工。

## 使用注意

`ods_item_review_di` 适合做评分分布、好评率、带图率和按商品聚合的口碑分析，查询时建议带 `dt` 过滤，例如 `SELECT sku_id, AVG(rating) FROM ods_item_review_di WHERE dt = '某分区' GROUP BY sku_id`。它不适合做交易金额或物流时效分析，那些去对应链路取。库里同家族还有噪声表：带日期备份如 `ods_item_review_di_bak_20250216`、`ods_item_review_di_bak_20251017`。它们是某次操作前的历史快照，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_item_review_di`。
