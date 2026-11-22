# 加工说明：ads_trade_refund_rate_monthly

## 加工逻辑

`ads_trade_refund_rate_monthly` 为 ADS-月度退款率，按下单月份汇总，当前 12 行。数据源为订单表 `ods_trade_order_di`，加工时以 `o.pay_time IS NOT NULL` 筛出已支付订单，再用 `DATE_FORMAT(o.order_time, '%Y-%m')` 将下单时间归集为月份并分组。

每个月份统计三项指标：已支付订单总数以 `COUNT(*)` 计；退款订单数以 `SUM(IF(o.order_status = 'refunded', 1, 0))` 计，即 `order_status` 等于 `refunded` 的订单计数；退款率以退款订单数除以订单总数，经 `ROUND(..., 4)` 保留四位小数。

```sql
SELECT DATE_FORMAT(o.order_time, '%Y-%m'), COUNT(*),
  SUM(IF(o.order_status = 'refunded', 1, 0)),
  ROUND(SUM(IF(o.order_status = 'refunded', 1, 0)) / COUNT(*), 4)
FROM ods_trade_order_di o WHERE o.pay_time IS NOT NULL
GROUP BY DATE_FORMAT(o.order_time, '%Y-%m')
```

需注意分母限定为已支付订单，故退款率反映的是支付后发生退款的比例，而非对全部下单订单计算。

## 依赖与调度

`ads_trade_refund_rate_monthly` 依赖一张上游表，即订单表 `ods_trade_order_di`。调度每日 05:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`ads_trade_refund_rate_monthly` 发布前执行四项检查：主键 / 粒度唯一，确保月份不重复；关键字段非空；行数与前一天相比波动超过 30% 告警；可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

若检查未通过，下游将受到影响。月份粒度重复会导致订单数与退款数被重复累加，退款率失真；行数异常下降通常表明 `ods_trade_order_di` 未抽取完整，退款率随之偏离真实水平。出现告警时应先核查上游，不得在未修复的情况下对外发布。
