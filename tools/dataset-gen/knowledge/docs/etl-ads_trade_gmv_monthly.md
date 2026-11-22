# 加工说明：ads_trade_gmv_monthly

## 加工逻辑

`ads_trade_gmv_monthly` 存 ADS-月度GMV报表，按下单月份汇总，当前 12 行，正好一年每月一行。

要点：

- 数据源是交易订单明细宽表 `dwd_trade_order_detail_di`，单表加工。
- 用 `DATE_FORMAT(order_date, '%Y-%m')` 把下单日期切成月份并分组。
- 只统计已支付部分，`is_paid = 1` 才计入。
- GMV 用 `SUM(IF(is_paid = 1, item_amount, 0))`。
- 支付订单数用 `COUNT(DISTINCT IF(is_paid = 1, order_id, NULL))` 去重。
- 支付买家数用 `COUNT(DISTINCT IF(is_paid = 1, buyer_id, NULL))` 去重。

```sql
SELECT DATE_FORMAT(order_date, '%Y-%m'), SUM(IF(is_paid = 1, item_amount, 0)),
  COUNT(DISTINCT IF(is_paid = 1, order_id, NULL)), COUNT(DISTINCT IF(is_paid = 1, buyer_id, NULL))
FROM dwd_trade_order_detail_di GROUP BY DATE_FORMAT(order_date, '%Y-%m')
```

## 依赖与调度

`ads_trade_gmv_monthly` 依赖一张上游表。

- 上游表：交易订单明细宽表 `dwd_trade_order_detail_di`。
- 调度：每日 05:00 开始。
- 触发：依赖上游全部成功后运行。
- 口径：处理前一天（T-1）的数据。

## 质量检查

`ads_trade_gmv_monthly` 发布前跑四项检查。

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。
- 可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

失败影响：

- 月份粒度重复，GMV 被重复累加，月度大盘失真。
- 行数骤降，说明 `dwd_trade_order_detail_di` 没抽全，GMV 被低估。
- 对账不平，订单数与买家数口径不可信，不应发布。
