# 加工说明：dws_trade_prov_refund

## 加工逻辑

`dws_trade_prov_refund` 系列将退款明细按收货省份粒度进行汇总，数据来源为 `dwd_trade_refund_di`，产出日、周、月三张表。日表 `dws_trade_prov_refund_1d` 以 `refund_date`、`receiver_province` 为分组键，统计退款笔数 `COUNT(*)` 与退款金额 `SUM(refund_amount)`：

```sql
INSERT INTO dws_trade_prov_refund_1d
SELECT refund_date, receiver_province, COUNT(*), SUM(refund_amount)
FROM dwd_trade_refund_di
GROUP BY refund_date, receiver_province
```

周表 `dws_trade_prov_refund_1w` 通过 `DATE_SUB(refund_date, INTERVAL WEEKDAY(refund_date) DAY)` 将日期归并至所在周的周一，月表 `dws_trade_prov_refund_1m` 通过 `DATE_FORMAT(refund_date, '%Y-%m-01')` 归并至月初，聚合口径与日表一致。本系列以退款时间 `refund_date` 为统计口径，与按下单或支付时间的汇总不可直接比对。三张表当前分别约 906 行（日）、536 行（周）、199 行（月）。

## 依赖与调度

`dws_trade_prov_refund` 的上游为退款明细表 `dwd_trade_refund_di`。调度策略为每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_trade_prov_refund` 产出后须通过四项质量检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账适用于退款笔数与退款金额等可加和指标。任一检查未通过，将导致下游分省退款分析在退款笔数或金额上产生偏差。
