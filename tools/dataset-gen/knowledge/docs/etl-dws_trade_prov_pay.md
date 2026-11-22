# 加工说明：dws_trade_prov_pay

## 加工逻辑

`dws_trade_prov_pay` 系列是把支付明细按收货省份汇到一起看支付情况，数据来自 `dwd_trade_pay_di`，每天产出日、周、月三张表。日表的逻辑是按 `pay_date` 和 `receiver_province` 分组，算支付笔数 `COUNT(*)` 和支付金额 `SUM(pay_amt)`：

```sql
INSERT INTO dws_trade_prov_pay_1d
SELECT pay_date, receiver_province, COUNT(*), SUM(pay_amt)
FROM dwd_trade_pay_di
GROUP BY pay_date, receiver_province
```

要跟新同事点一句：这张表按的是支付时间 `pay_date`，统计的是支付行为，和按下单时间的省份下单表口径不同，分析时别把两者当同一件事。周表 `dws_trade_prov_pay_1w` 用 `DATE_SUB(pay_date, INTERVAL WEEKDAY(pay_date) DAY)` 把日期归到周一，月表 `dws_trade_prov_pay_1m` 用 `DATE_FORMAT(pay_date, '%Y-%m-01')` 归到月初，其余逻辑一致。三张表现在大约是 5,149 行（日）、848 行（周）、193 行（月）。

## 依赖与调度

`dws_trade_prov_pay` 的上游就是支付明细表 `dwd_trade_pay_di`。每天 03:30 开始跑，等上游全部成功后才启动，处理的是前一天（T-1）的数据。

## 质量检查

`dws_trade_prov_pay` 落表后会跑四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账看的是支付笔数和支付金额这类能加和的指标。哪一项没过，下游分省支付分析就会跟着失真，金额或笔数对不上。
