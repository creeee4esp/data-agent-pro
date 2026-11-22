# 加工说明：dwd_trade_refund_di

## 加工逻辑

`dwd_trade_refund_di` 存 DWD-退款明细，一条退款一行，当前 1,031 行。

要点：

- 以退款表 `ods_pay_refund_di` 为主表。
- 关联订单表 `ods_trade_order_di`，连接键 `order_id`，补收货省份。
- 取字段：`refund_id`、`order_id`、`buyer_id`、`refund_amount`、`refund_reason`、`receiver_province`。
- 退款日期用 `DATE(r.finish_time)`，取退款完成时间。
- 分区 `dt` 沿用上游。

```sql
SELECT r.refund_id, r.order_id, o.buyer_id, r.refund_amount, r.refund_reason,
  o.receiver_province, DATE(r.finish_time), r.dt FROM ods_pay_refund_di r JOIN ods_trade_order_di o ON o.order_id = r.order_id
```

## 依赖与调度

`dwd_trade_refund_di` 依赖两张上游表。

- 上游表：退款表 `ods_pay_refund_di`、订单表 `ods_trade_order_di`。
- 调度：每日 02:30 开始。
- 触发：依赖上游全部成功后运行。
- 口径：处理前一天（T-1）的数据。

## 质量检查

`dwd_trade_refund_di` 发布前跑三项检查。

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。

失败影响：

- `refund_id` 重复，退款笔数和退款金额被重复累加。
- `refund_reason` 等字段为空，按退款原因分析的下游会丢分组。
- 行数骤降，说明 `ods_pay_refund_di` 没抽全，退款规模被低估。
