# 加工说明：dws_lgs_carrier_ship

## 加工逻辑

`dws_lgs_carrier_ship` 系列把发货明细按承运商粒度汇总，从 `dwd_lgs_shipment_di` 读数，产出日、周、月三张表。日表按 `ship_date`、`carrier` 分组，算发货单量 `COUNT(*)`、签收单量 `SUM(is_signed)` 和平均发货时长 `ROUND(AVG(ship_hours), 2)`：

```sql
INSERT INTO dws_lgs_carrier_ship_1d
SELECT ship_date, carrier, COUNT(*), SUM(is_signed), ROUND(AVG(ship_hours), 2)
FROM dwd_lgs_shipment_di
GROUP BY ship_date, carrier
```

取数例子：查某承运商某天发货量，`SELECT * FROM dws_lgs_carrier_ship_1d WHERE ship_date = '2025-10-25' AND carrier = '顺丰'`；要看某承运商某月签收量，直接查月表 `dws_lgs_carrier_ship_1m`，`WHERE ship_date = '2025-10-01'`（月表日期已归到月初）。周表 `dws_lgs_carrier_ship_1w` 的日期是 `DATE_SUB(ship_date, INTERVAL WEEKDAY(ship_date) DAY)` 算出的周一。三张表当前分别约 1,468 行（日）、212 行（周）、52 行（月）。

## 依赖与调度

`dws_lgs_carrier_ship` 的上游是发货明细表 `dwd_lgs_shipment_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_lgs_carrier_ship` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。例如发货单量汇总应与 `dwd_lgs_shipment_di` 当日总数一致。检查失败会直接影响下游承运商时效看板，签收率、平均时长等口径会算错。
