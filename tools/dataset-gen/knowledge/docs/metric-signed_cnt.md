# 指标说明：已签收单数（signed_cnt）

## 计算公式

`signed_cnt` 统计的是一段时间内已经签收的物流单数量，口径是 `SUM(is_signed)`。你可以把它理解成：每条发货明细上有一个 `is_signed` 标记，已签收记 1、未签收记 0，把这些标记加起来就是已签收单数。时间归属跟发货单保持一致，看发货日期 `ship_date`，哪天发货就归到哪天，这样签收数和发货数在同一张表里能直接对照。该指标可加和，各天、各周的结果能直接相加，日粒度求和即得更长周期的已签收单数。

## 取哪张表

按统计周期和维度来选表是取数的第一步，日粒度用 `_1d`，周粒度用 `_1w`，月粒度用 `_1m`。

- 承运商维度：日用 `dws_lgs_carrier_ship_1d`，周用 `dws_lgs_carrier_ship_1w`，月用 `dws_lgs_carrier_ship_1m`。
- 省份维度：日用 `dws_lgs_prov_ship_1d`，周用 `dws_lgs_prov_ship_1w`，月用 `dws_lgs_prov_ship_1m`。
- 仓库维度：日用 `dws_lgs_warehouse_ship_1d`，周用 `dws_lgs_warehouse_ship_1w`，月用 `dws_lgs_warehouse_ship_1m`。

这几张表和发货单数同源，想算签收进度时在同一张表里拿 `signed_cnt` 和发货单数对比即可。

## 来源与历史变化

数据一直追到明细就是发货明细表 `dwd_lgs_shipment_di`，上面各 `dws` 表都是在它之上按维度聚合出来的。历史上有一条口径说明值得记住：2025-07-12 商品数据组发布，签收以物流单 `sign_time` 不为空为准。也就是说，判断是否签收看的是物流单上的签收时间字段，有值才算签收，这决定了 `is_signed` 怎么打标。
