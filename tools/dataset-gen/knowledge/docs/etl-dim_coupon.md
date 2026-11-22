# 加工说明：dim_coupon

## 加工逻辑

`dim_coupon` 为 DIM-优惠券维表，当前 30 行，一张券一行。

要点：

- 数据源是优惠券定义表 `ods_mkt_coupon_di`，单表直取，不做聚合。
- 选取字段：`coupon_id`、`coupon_name`、`coupon_type`、`threshold_amt`、`face_value`。
- 粒度为优惠券，主键是 `coupon_id`。

```sql
INSERT INTO dim_coupon SELECT coupon_id, coupon_name, coupon_type, threshold_amt, face_value FROM ods_mkt_coupon_di
```

## 依赖与调度

`dim_coupon` 依赖一张上游表。

- 上游表：优惠券定义表 `ods_mkt_coupon_di`。
- 调度：每日 02:00 开始。
- 触发：依赖上游全部成功后运行。
- 口径：处理前一天（T-1）的数据。

## 质量检查

`dim_coupon` 发布前跑三项检查。

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。

失败影响：

- `coupon_id` 重复，下游按券关联会多匹配，核销统计翻倍。
- `coupon_type` 等关键字段为空，按券类型汇总的下游会丢分组或归入空值。
- 行数骤降，说明 `ods_mkt_coupon_di` 没抽全，部分券在下游关联不上。
