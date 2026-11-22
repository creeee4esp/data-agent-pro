# 加工说明：dws_trade_user_order

## 加工逻辑

`dws_trade_user_order` 系列将订单明细按买家用户粒度进行汇总，数据来源为 `dwd_trade_order_detail_di`，产出日、周、月三张表。日表 `dws_trade_user_order_1d` 以 `order_date`、`buyer_id` 为分组键，统计下单数 `COUNT(DISTINCT order_id)`、件数 `SUM(qty)` 与已支付金额 `SUM(IF(is_paid = 1, item_amount, 0))`：

```sql
INSERT INTO dws_trade_user_order_1d
SELECT order_date, buyer_id,
       COUNT(DISTINCT order_id), SUM(qty),
       SUM(IF(is_paid = 1, item_amount, 0))
FROM dwd_trade_order_detail_di
GROUP BY order_date, buyer_id
```

周表 `dws_trade_user_order_1w` 通过 `DATE_SUB(order_date, INTERVAL WEEKDAY(order_date) DAY)` 将日期归并至所在周的周一，月表 `dws_trade_user_order_1m` 通过 `DATE_FORMAT(order_date, '%Y-%m-01')` 归并至月初，聚合口径与日表一致。其中下单数采用 `COUNT(DISTINCT order_id)` 去重，已支付金额仅累加 `is_paid = 1` 的明细。三张表当前分别约 19,102 行（日）、15,568 行（周）、9,300 行（月）。

## 依赖与调度

`dws_trade_user_order` 的上游为订单明细表 `dwd_trade_order_detail_di`。调度策略为每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_trade_user_order` 产出后须通过四项质量检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账适用于件数与已支付金额等可加和指标，去重后的下单数不参与简单加和对账。任一检查未通过，将导致下游用户下单分析在件数或已支付金额上产生偏差。
