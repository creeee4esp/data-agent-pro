# 加工说明：ads_trade_province_sales_monthly

## 加工逻辑

`ads_trade_province_sales_monthly` 存 ADS-省份月度销售额，按"下单月份 + 收货省份"汇总，当前 192 行。数据源是交易订单明细宽表 `dwd_trade_order_detail_di`，用 `DATE_FORMAT(order_date, '%Y-%m')` 把下单日期切成月份，和 `receiver_province` 一起分组，销售额用 `SUM(IF(is_paid = 1, item_amount, 0))`。

```sql
SELECT DATE_FORMAT(order_date, '%Y-%m'), receiver_province,
  SUM(IF(is_paid = 1, item_amount, 0)) FROM dwd_trade_order_detail_di
GROUP BY DATE_FORMAT(order_date, '%Y-%m'), receiver_province
```

有两个容易误用的地方要点一下。第一，省份口径用的是 `receiver_province`（收货省份），不是买家注册地，分析区域销售时别把它当成用户归属地。第二，销售额只算已支付，`is_paid` 不等于 1 的明细按 0 处理，所以这里的金额天然小于订单提交总额，不要拿它去对未支付的下单金额。

## 依赖与调度

`ads_trade_province_sales_monthly` 依赖一张上游表，交易订单明细宽表 `dwd_trade_order_detail_di`。调度每日 05:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`ads_trade_province_sales_monthly` 发布前跑四项检查：主键 / 粒度唯一，保证"月份 + 收货省份"不重复；关键字段非空；行数与前一天相比波动超过 30% 告警；可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

检查失败会直接影响下游。粒度重复会让某省份销售额被重复累加，省份排名和占比算错；行数骤降通常是 `dwd_trade_order_detail_di` 没抽全，直接用会低估区域销售。看到告警先回查上游再发布。
