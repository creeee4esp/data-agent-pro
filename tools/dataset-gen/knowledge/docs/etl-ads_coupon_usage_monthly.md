# 加工说明：ads_coupon_usage_monthly

## 加工逻辑

`ads_coupon_usage_monthly` 存的是 ADS-优惠券月度使用情况，按"领券月份 + 券类型"两个维度汇总，当前共 39 行。加工时把领券记录 `ods_mkt_coupon_receive_di` 和优惠券维表 `dim_coupon` 按 `coupon_id` 关联，拿到每张券的 `coupon_type`，再用 `DATE_FORMAT(r.receive_time, '%Y-%m')` 把领券时间切成月份。

每个"月份 + 券类型"分组里算三件事：领券总张数用 `COUNT(*)`；已核销张数用 `SUM(r.use_status = 'used')`，即 `use_status` 等于 `used` 的记录数；核销率用已核销除以领券总数，保留四位小数。

```sql
SELECT DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type, COUNT(*),
  SUM(r.use_status = 'used'), ROUND(SUM(r.use_status = 'used') / COUNT(*), 4)
FROM ods_mkt_coupon_receive_di r JOIN dim_coupon c ON c.coupon_id = r.coupon_id
GROUP BY DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type
```

举个取数例子：要看"满减券 2024-06 的核销率"，直接按月份等于 `2024-06`、券类型等于满减券过滤即可，不用再回 `ods_mkt_coupon_receive_di` 自己算。

## 依赖与调度

`ads_coupon_usage_monthly` 依赖两张上游表：优惠券维表 `dim_coupon` 和领券记录明细 `ods_mkt_coupon_receive_di`。调度每日 05:00 开始，等上游全部成功后才运行，处理前一天（T-1）的数据。所以今天早上看到的结果，覆盖的是截至昨天的领券与核销情况。

## 质量检查

`ads_coupon_usage_monthly` 上线前跑四项检查。一是主键 / 粒度唯一，保证"月份 + 券类型"不重复；二是关键字段非空；三是行数与前一天相比波动超过 30% 告警；四是可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

检查失败时，下游读到的核销率和领券张数就不可信。比如粒度重复会让同一个月的券类型被算两遍，核销率偏离真实值；行数骤降往往说明 `ods_mkt_coupon_receive_di` 当天没抽全，直接用会低估券的使用量。遇到告警应先定位上游，不要带病发布。
