# 加工说明：dws_trade_sku_order

## 加工逻辑

`dws_trade_sku_order` 系列把订单明细按商品 SKU 汇总下单情况，从 `dwd_trade_order_detail_di` 读数，产出日、周、月三张表。日表按 `order_date`、`sku_id` 分组，统计下单数 `COUNT(DISTINCT order_id)`、件数 `SUM(qty)` 和已支付金额 `SUM(IF(is_paid = 1, item_amount, 0))`：

```sql
INSERT INTO dws_trade_sku_order_1d
SELECT order_date, sku_id,
       COUNT(DISTINCT order_id), SUM(qty),
       SUM(IF(is_paid = 1, item_amount, 0))
FROM dwd_trade_order_detail_di
GROUP BY order_date, sku_id
```

周表 `dws_trade_sku_order_1w` 用 `DATE_SUB(order_date, INTERVAL WEEKDAY(order_date) DAY)` 把日期归到周一，月表 `dws_trade_sku_order_1m` 用 `DATE_FORMAT(order_date, '%Y-%m-01')` 归到月初。

有两处容易误用：一是下单数用 `COUNT(DISTINCT order_id)`，同一订单含同一 SKU 多条明细只算一单，想在 SKU 之上再汇总订单数不能直接把各 SKU 的下单数相加，否则跨 SKU 的订单会被重复计数；二是金额只累加 `is_paid = 1` 的明细，是已支付口径，不等于下单金额。三张表当前分别约 17,191 行（日）、8,182 行（周）、3,181 行（月）。

## 依赖与调度

`dws_trade_sku_order` 的上游是订单明细表 `dwd_trade_order_detail_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_trade_sku_order` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账针对件数、已支付金额等可加和指标，下单数因去重不参与简单加和对账。检查失败会导致下游 SKU 销售分析的件数或金额失真。
