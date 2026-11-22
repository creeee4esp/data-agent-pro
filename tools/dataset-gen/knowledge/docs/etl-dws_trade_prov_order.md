# 加工说明：dws_trade_prov_order

## 加工逻辑

`dws_trade_prov_order` 系列把订单明细按收货省份汇总下单情况，源表 `dwd_trade_order_detail_di`，产出日、周、月三张表。

- 分组键：`order_date`、`receiver_province`。
- 指标：下单数 `COUNT(DISTINCT order_id)`、件数 `SUM(qty)`、已支付金额 `SUM(IF(is_paid = 1, item_amount, 0))`。

```sql
INSERT INTO dws_trade_prov_order_1d
SELECT order_date, receiver_province,
       COUNT(DISTINCT order_id), SUM(qty),
       SUM(IF(is_paid = 1, item_amount, 0))
FROM dwd_trade_order_detail_di
GROUP BY order_date, receiver_province
```

- 周表 `dws_trade_prov_order_1w`：日期 `DATE_SUB(order_date, INTERVAL WEEKDAY(order_date) DAY)` 归到周一。
- 月表 `dws_trade_prov_order_1m`：日期 `DATE_FORMAT(order_date, '%Y-%m-01')` 归到月初。
- 下单数去重；金额仅含 `is_paid = 1`。
- 行数：日约 5,278、周约 848、月约 192。

## 依赖与调度

`dws_trade_prov_order` 上游为订单明细表 `dwd_trade_order_detail_di`。

- 调度：每日 03:30 开始。
- 触发：依赖上游全部成功后运行。
- 范围：处理前一天（T-1）的数据。

## 质量检查

`dws_trade_prov_order` 执行四项检查：

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。
- 可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

检查失败影响下游分省下单分析，件数、已支付金额口径会错。
