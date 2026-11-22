# 加工说明：dws_trade_user_pay

## 加工逻辑

`dws_trade_user_pay` 系列将支付明细按买家用户粒度进行汇总，数据来源为 `dwd_trade_pay_di`，产出日、周、月三张表。日表 `dws_trade_user_pay_1d` 以 `pay_date`、`buyer_id` 为分组键，统计支付笔数 `COUNT(*)` 与支付金额 `SUM(pay_amt)`：

```sql
INSERT INTO dws_trade_user_pay_1d
SELECT pay_date, buyer_id, COUNT(*), SUM(pay_amt)
FROM dwd_trade_pay_di
GROUP BY pay_date, buyer_id
```

周表 `dws_trade_user_pay_1w` 通过 `DATE_SUB(pay_date, INTERVAL WEEKDAY(pay_date) DAY)` 将日期归并至所在周的周一，月表 `dws_trade_user_pay_1m` 通过 `DATE_FORMAT(pay_date, '%Y-%m-01')` 归并至月初，聚合口径与日表一致。本系列以支付时间 `pay_date` 为统计口径，与按下单时间的用户下单汇总不可直接比对。三张表当前分别约 17,293 行（日）、14,350 行（周）、8,812 行（月）。

## 依赖与调度

`dws_trade_user_pay` 的上游为支付明细表 `dwd_trade_pay_di`。调度策略为每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_trade_user_pay` 产出后须通过四项质量检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账适用于支付笔数与支付金额等可加和指标。任一检查未通过，将导致下游用户支付分析在支付笔数或金额上产生偏差。
