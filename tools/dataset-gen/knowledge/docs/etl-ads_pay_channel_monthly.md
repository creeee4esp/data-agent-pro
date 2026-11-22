# 加工说明：ads_pay_channel_monthly

## 加工逻辑

`ads_pay_channel_monthly` 存的是 ADS-支付渠道月度报表，按"支付月份 + 支付渠道"两个维度把支付情况汇总起来，现在一共 49 行。你可以把它理解成"每个月、每个渠道收了多少笔、收了多少钱"的一张账。

加工本身很直接：从支付明细 `dwd_trade_pay_di` 读数，用 `DATE_FORMAT(pay_date, '%Y-%m')` 把支付日期切成月份，再和 `pay_channel` 一起分组。每个分组里，支付笔数用 `COUNT(*)` 数，支付金额用 `SUM(pay_amt)` 加总。

```sql
SELECT DATE_FORMAT(pay_date, '%Y-%m'), pay_channel, COUNT(*), SUM(pay_amt)
FROM dwd_trade_pay_di GROUP BY DATE_FORMAT(pay_date, '%Y-%m'), pay_channel
```

这里要提醒新同事一句：金额口径跟的是 `dwd_trade_pay_di` 里的 `pay_amt`，是支付侧的实付金额，不要拿它当成交易侧的订单金额去对 GMV。

## 依赖与调度

`ads_pay_channel_monthly` 只依赖一张上游表，就是支付明细 `dwd_trade_pay_di`。调度上每天 05:00 启动，但不会一到点就跑，而是等上游全部成功之后才运行，处理的是前一天（T-1）的数据。换句话说，你今天看到的数，是截止到昨天的支付情况。

## 质量检查

`ads_pay_channel_monthly` 上线前要过四道检查：主键 / 粒度唯一，保证"月份 + 渠道"不重复；关键字段非空；行数与前一天相比波动超过 30% 告警；可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

这几项一旦挂了，影响会直接传到下游。粒度重复会让某个渠道的笔数和金额被重复累加，渠道占比就算错了；行数突然大跌，多半是 `dwd_trade_pay_di` 当天没抽全，这时候用它算渠道金额会明显偏低。所以看到告警先别急着对外发，回头把上游确认清楚再说。
