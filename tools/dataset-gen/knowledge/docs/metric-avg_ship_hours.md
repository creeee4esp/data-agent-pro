# 指标说明：平均发货时长（小时）（avg_ship_hours）

## 计算公式

`avg_ship_hours` 定义为一段时间内发货时长的平均值，计算口径为 `ROUND(AVG(ship_hours), 2)`，即对明细行的发货时长取平均并保留两位小数，单位为小时。时间归属以发货日期 `ship_date` 为准。该指标为平均值类指标，不具备可加性：各天、各周的平均值不能直接相加，也不能跨天简单取算术平均，若需更长周期的平均发货时长，应回到对应粒度重新按明细计算，不能用日均值累加或平均得到。

## 取哪张表

统计表的选择取决于统计周期与分析维度。按周期区分，日粒度使用 `_1d` 后缀表，周粒度使用 `_1w` 后缀表，月粒度使用 `_1m` 后缀表。

- 承运商维度：日用 `dws_lgs_carrier_ship_1d`，周用 `dws_lgs_carrier_ship_1w`，月用 `dws_lgs_carrier_ship_1m`。
- 省份维度：日用 `dws_lgs_prov_ship_1d`，周用 `dws_lgs_prov_ship_1w`，月用 `dws_lgs_prov_ship_1m`。
- 仓库维度：日用 `dws_lgs_warehouse_ship_1d`，周用 `dws_lgs_warehouse_ship_1w`，月用 `dws_lgs_warehouse_ship_1m`。

若需按月查看发货时长的履约表现，可使用月度报表 `ads_lgs_delivery_monthly`。

## 来源与历史变化

`avg_ship_hours` 来源于发货明细表 `dwd_lgs_shipment_di`，上述各汇总表与报表均由该明细表按维度聚合而成。历史变更方面，2025-12-16 由流量数据组发布说明：发货时长从支付时间算到发货时间。进行口径核对时须以该区间定义为准。
