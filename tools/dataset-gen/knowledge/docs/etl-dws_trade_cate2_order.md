# 加工说明：dws_trade_cate2_order

## 加工逻辑

`dws_trade_cate2_order` 系列是把订单明细按二级品类汇总，看每个二级品类的下单表现。数据来自 `dwd_trade_order_detail_di`，每天产出日、周、月三张表。日表的逻辑是按 `order_date`、`category2_id`、`category2_name` 分组，算下单数 `COUNT(DISTINCT order_id)`、件数 `SUM(qty)` 和已支付金额 `SUM(IF(is_paid = 1, item_amount, 0))`：

```sql
INSERT INTO dws_trade_cate2_order_1d
SELECT order_date, category2_id, category2_name,
       COUNT(DISTINCT order_id), SUM(qty),
       SUM(IF(is_paid = 1, item_amount, 0))
FROM dwd_trade_order_detail_di
GROUP BY order_date, category2_id, category2_name
```

跟新同事说清楚两点：下单数做了 `COUNT(DISTINCT order_id)` 去重，同一订单里同品类多条明细只算一单；金额只累加 `is_paid = 1` 的明细，是已支付口径。周表 `dws_trade_cate2_order_1w` 用 `DATE_SUB(order_date, INTERVAL WEEKDAY(order_date) DAY)` 把日期落到周一，月表 `dws_trade_cate2_order_1m` 用 `DATE_FORMAT(order_date, '%Y-%m-01')` 落到月初，其他都一样。三张表大约是 5,078 行（日）、795 行（周）、180 行（月）。

## 依赖与调度

`dws_trade_cate2_order` 的上游就是订单明细宽表 `dwd_trade_order_detail_di`。每天 03:30 开始跑，等上游全部成功后才启动，处理的是前一天（T-1）的数据。

## 质量检查

`dws_trade_cate2_order` 产出后会跑四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账看的是件数和已支付金额这类可加和指标。只要有一项没过，下游二级品类的下单分析就会跟着失真，件数或金额对不上。
