# 指标说明：浏览量（pv）

## 计算公式

`pv` 统计的是一段时间内的访问次数，口径是 `COUNT(*)`，对访问明细逐行计数，访问一次就算一次，不做去重。时间归属看访问日期 `visit_date`，哪天访问就归到哪天。和访客数不同，浏览量是可加和的：同一个人访问多次会被重复计入，各天、各周的结果可以直接相加，日粒度求和就能得到更长周期的浏览量。正因为它可加，做周、月趋势时拿日表累加没有问题。

## 取哪张表

按统计周期和维度选表是取数的第一步，日粒度用 `_1d`，周粒度用 `_1w`，月粒度用 `_1m`。

- 一级品类维度：日用 `dws_traffic_cate1_visit_1d`，周用 `dws_traffic_cate1_visit_1w`，月用 `dws_traffic_cate1_visit_1m`。
- SKU 维度：日用 `dws_traffic_sku_visit_1d`，周用 `dws_traffic_sku_visit_1w`，月用 `dws_traffic_sku_visit_1m`。
- 访问渠道维度：日用 `dws_traffic_vchannel_visit_1d`，周用 `dws_traffic_vchannel_visit_1w`，月用 `dws_traffic_vchannel_visit_1m`。
- 访客维度：日用 `dws_traffic_visitor_visit_1d`，周用 `dws_traffic_visitor_visit_1w`，月用 `dws_traffic_visitor_visit_1m`。

想算人均浏览量时，在同一张表里拿 `pv` 和去重访客数相除即可。

## 来源与历史变化

数据一路追到明细就是访问明细表 `dwd_traffic_visit_di`，上面各 `dws` 表都是在它之上按不同维度聚合出来的。这个指标自上线以来未调整计算规则，一直是逐行计数，历史数据和当前数据口径一致，可以放心做趋势对比。
