# 指标说明：发货单数（ship_cnt）

## 计算公式

`ship_cnt` 定义为一段时间内发生的发货单据数量，计算口径为 `COUNT(*)`，对发货明细记录逐行计数。时间归属以发货日期 `ship_date` 为准，发货动作发生在哪一天即计入哪一天。该指标具备可加性，各行、各天、各周的结果可直接相加，日粒度汇总求和即可得到更长周期的发货单数。

## 取哪张表

统计表的选择取决于统计周期与分析维度。按周期区分，日粒度使用 `_1d` 后缀表，周粒度使用 `_1w` 后缀表，月粒度使用 `_1m` 后缀表。

- 承运商维度：日用 `dws_lgs_carrier_ship_1d`，周用 `dws_lgs_carrier_ship_1w`，月用 `dws_lgs_carrier_ship_1m`。
- 省份维度：日用 `dws_lgs_prov_ship_1d`，周用 `dws_lgs_prov_ship_1w`，月用 `dws_lgs_prov_ship_1m`。
- 仓库维度：日用 `dws_lgs_warehouse_ship_1d`，周用 `dws_lgs_warehouse_ship_1w`，月用 `dws_lgs_warehouse_ship_1m`。

若需按月查看物流履约概况，可使用月度报表 `ads_lgs_delivery_monthly`。

## 来源与历史变化

`ship_cnt` 来源于发货明细表 `dwd_lgs_shipment_di`，上述各汇总表与报表均由该明细表按不同维度聚合而成。该指标自上线以来未调整计算规则，历史口径与当前口径一致，可直接用于跨期趋势比较。
