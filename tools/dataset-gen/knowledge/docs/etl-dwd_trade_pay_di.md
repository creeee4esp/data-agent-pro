# 加工说明：dwd_trade_pay_di

## 加工逻辑

`dwd_trade_pay_di` 存的是 DWD-支付明细，一条支付流水一行，当前 18,031 行。加工把支付流水表 `ods_pay_order_di` 和订单表 `ods_trade_order_di` 按 `order_id` 关联，给每条支付补上收货省份。

字段上取支付侧的 `pay_id`、`order_id`、`uid`、`pay_channel`、`pay_amt`，从订单侧带出 `receiver_province`，支付日期用 `DATE(p.pay_time)`，分区 `dt` 沿用上游。

```sql
SELECT p.pay_id, p.order_id, p.uid, p.pay_channel, p.pay_amt, o.receiver_province,
  DATE(p.pay_time), p.dt FROM ods_pay_order_di p JOIN ods_trade_order_di o ON o.order_id = p.order_id
```

举个取数例子：要算"某支付渠道的月度金额"，直接按 `pay_channel` 分组、对 `pay_amt` 求和即可；要看"某省份支付笔数"，按 `receiver_province` 分组数行即可，不用再回订单表关联省份。

## 依赖与调度

`dwd_trade_pay_di` 依赖两张上游表：支付流水表 `ods_pay_order_di` 和订单表 `ods_trade_order_di`。调度每日 02:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dwd_trade_pay_di` 发布前跑三项检查：主键 / 粒度唯一，保证 `pay_id` 不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查失败会直接影响下游。`pay_id` 重复会让支付笔数和金额被重复累加，渠道金额算错；`pay_channel` 或 `receiver_province` 为空会让按渠道、省份汇总的报表丢分组；行数骤降往往是 `ods_pay_order_di` 没抽全，下游支付金额偏低。看到告警先定位上游再发布。
