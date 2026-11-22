# 加工说明：dws_lgs_warehouse_ship

## 加工逻辑

`dws_lgs_warehouse_ship` 系列将发货明细按仓库粒度进行汇总，数据来源为 `dwd_lgs_shipment_di`，产出日、周、月三张表。日表 `dws_lgs_warehouse_ship_1d` 以 `ship_date`、`warehouse_id`、`warehouse_region` 为分组键，统计发货单量 `COUNT(*)`、签收单量 `SUM(is_signed)` 与平均发货时长 `ROUND(AVG(ship_hours), 2)`：

```sql
INSERT INTO dws_lgs_warehouse_ship_1d
SELECT ship_date, warehouse_id, warehouse_region,
       COUNT(*), SUM(is_signed), ROUND(AVG(ship_hours), 2)
FROM dwd_lgs_shipment_di
GROUP BY ship_date, warehouse_id, warehouse_region
```

周表 `dws_lgs_warehouse_ship_1w` 通过 `DATE_SUB(ship_date, INTERVAL WEEKDAY(ship_date) DAY)` 将日期归并至所在周的周一，月表 `dws_lgs_warehouse_ship_1m` 通过 `DATE_FORMAT(ship_date, '%Y-%m-01')` 归并至月初，聚合口径与日表一致。三张表当前分别约 2,137 行（日）、318 行（周）、77 行（月）。平均发货时长 `ROUND(AVG(ship_hours), 2)` 为不可加和指标，更大范围的均值须回源明细重新计算。

## 依赖与调度

`dws_lgs_warehouse_ship` 的上游为发货明细表 `dwd_lgs_shipment_di`。调度策略为每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_lgs_warehouse_ship` 产出后须通过四项质量检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账仅适用于发货单量、签收单量等可加和指标。任一检查未通过，将导致下游仓库维度发货时效分析的签收率与平均时长产生偏差。
