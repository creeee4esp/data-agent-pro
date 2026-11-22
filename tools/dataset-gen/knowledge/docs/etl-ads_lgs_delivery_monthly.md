# 加工说明：ads_lgs_delivery_monthly

## 加工逻辑

`ads_lgs_delivery_monthly` 存 ADS-仓库月度物流时效，按"发货月份 + 仓库大区"汇总，当前 77 行。

要点：

- 数据源是物流明细 `dwd_lgs_shipment_di`，单表加工。
- 用 `DATE_FORMAT(ship_date, '%Y-%m')` 把发货日期切成月份。
- 按月份和 `warehouse_region` 分组。
- 发货单量用 `COUNT(*)`。
- 平均发货时长用 `ROUND(AVG(ship_hours), 2)`，保留两位小数。
- 平均送达时长用 `ROUND(AVG(deliver_hours), 2)`，保留两位小数。

```sql
SELECT DATE_FORMAT(ship_date, '%Y-%m'), warehouse_region, COUNT(*),
  ROUND(AVG(ship_hours), 2), ROUND(AVG(deliver_hours), 2) FROM dwd_lgs_shipment_di
GROUP BY DATE_FORMAT(ship_date, '%Y-%m'), warehouse_region
```

## 依赖与调度

`ads_lgs_delivery_monthly` 依赖一张上游表。

- 上游表：物流明细 `dwd_lgs_shipment_di`。
- 调度：每日 05:00 开始。
- 触发：依赖上游全部成功后运行。
- 口径：处理前一天（T-1）的数据。

## 质量检查

`ads_lgs_delivery_monthly` 发布前跑四项检查。

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。
- 可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

失败影响：

- 粒度重复，同一仓库大区的发货单量被多算，时效均值被拉偏。
- 行数骤降，说明 `dwd_lgs_shipment_di` 没抽全，下游会低估发货规模。
- 对账不平，平均时效不可信，不应发布。
