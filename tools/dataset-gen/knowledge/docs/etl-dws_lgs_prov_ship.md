# 加工说明：dws_lgs_prov_ship

## 加工逻辑

`dws_lgs_prov_ship` 系列把发货明细按收货省份粒度汇总，从 `dwd_lgs_shipment_di` 读数，产出日、周、月三张表。日表按 `ship_date`、`receiver_province` 分组，统计发货单量 `COUNT(*)`、签收单量 `SUM(is_signed)` 和平均发货时长 `ROUND(AVG(ship_hours), 2)`：

```sql
INSERT INTO dws_lgs_prov_ship_1d
SELECT ship_date, receiver_province, COUNT(*), SUM(is_signed), ROUND(AVG(ship_hours), 2)
FROM dwd_lgs_shipment_di
GROUP BY ship_date, receiver_province
```

周表 `dws_lgs_prov_ship_1w` 用 `DATE_SUB(ship_date, INTERVAL WEEKDAY(ship_date) DAY)` 把日期归到周一，月表 `dws_lgs_prov_ship_1m` 用 `DATE_FORMAT(ship_date, '%Y-%m-01')` 归到月初。

有一处容易误用：粒度是收货省份 `receiver_province`，不是发货仓所在地，想看仓库维度要查仓库粒度的汇总表，别用本表反推。另外平均发货时长 `ROUND(AVG(ship_hours), 2)` 不可加和，跨省或跨周期求均值需回明细重算。三张表当前分别约 5,088 行（日）、848 行（周）、205 行（月）。

## 依赖与调度

`dws_lgs_prov_ship` 的上游是发货明细表 `dwd_lgs_shipment_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_lgs_prov_ship` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账只针对发货单量、签收单量等可加和指标。检查失败会导致下游分省物流时效分析的签收率和时长失真。
