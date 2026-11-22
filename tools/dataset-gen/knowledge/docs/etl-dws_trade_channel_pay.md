# 加工说明：dws_trade_channel_pay

## 加工逻辑

`dws_trade_channel_pay` 系列把支付明细按支付渠道汇总，从 `dwd_trade_pay_di` 读数，产出日、周、月三张表。日表按 `pay_date`、`pay_channel` 分组，统计支付笔数 `COUNT(*)` 和支付金额 `SUM(pay_amt)`：

```sql
INSERT INTO dws_trade_channel_pay_1d
SELECT pay_date, pay_channel, COUNT(*), SUM(pay_amt)
FROM dwd_trade_pay_di
GROUP BY pay_date, pay_channel
```

周表 `dws_trade_channel_pay_1w` 用 `DATE_SUB(pay_date, INTERVAL WEEKDAY(pay_date) DAY)` 把日期归到周一，月表 `dws_trade_channel_pay_1m` 用 `DATE_FORMAT(pay_date, '%Y-%m-01')` 归到月初。

有一处容易误用：本系列按支付时间 `pay_date` 汇总，统计口径是支付而非下单，和按 `order_date` 的下单汇总表不是一回事，同一笔交易的下单日和支付日可能不同，跨天时两边对不上是正常的，别硬拉平。三张表当前分别约 1,459 行（日）、212 行（周）、49 行（月）。

## 依赖与调度

`dws_trade_channel_pay` 的上游是支付明细表 `dwd_trade_pay_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_trade_channel_pay` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账针对支付笔数、支付金额等可加和指标。检查失败会导致下游支付渠道分析的金额或笔数失真。
