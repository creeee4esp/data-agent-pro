# 加工说明：dws_trade_reason_refund

## 加工逻辑

`dws_trade_reason_refund` 系列把退款明细按退款原因汇总，从 `dwd_trade_refund_di` 读数，产出日、周、月三张表。日表按 `refund_date`、`refund_reason` 分组，算退款笔数 `COUNT(*)` 和退款金额 `SUM(refund_amount)`：

```sql
INSERT INTO dws_trade_reason_refund_1d
SELECT refund_date, refund_reason, COUNT(*), SUM(refund_amount)
FROM dwd_trade_refund_di
GROUP BY refund_date, refund_reason
```

取数例子：查某天各退款原因的退款金额，`SELECT refund_reason, SUM(refund_amount) FROM dws_trade_reason_refund_1d WHERE refund_date = '2025-10-25'`；看某原因某月退款笔数，直接查月表 `dws_trade_reason_refund_1m WHERE refund_date = '2025-10-01'`（月表日期已归到月初）。周表 `dws_trade_reason_refund_1w` 的日期是 `DATE_SUB(refund_date, INTERVAL WEEKDAY(refund_date) DAY)` 算出的周一。三张表当前分别约 736 行（日）、211 行（周）、52 行（月）。

## 依赖与调度

`dws_trade_reason_refund` 的上游是退款明细表 `dwd_trade_refund_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_trade_reason_refund` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。例如全部原因的退款金额合计应与 `dwd_trade_refund_di` 当日总额一致。检查失败会直接影响下游退款原因分布分析，笔数或金额会算错。
