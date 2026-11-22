# 指标说明：总停留秒数（stay_seconds）

## 计算公式

`stay_seconds` 定义为一段时间内访问停留秒数的总和，计算口径为 `SUM(stay_seconds)`，对访问明细行的停留秒数逐行累加，单位为秒。时间归属以访问日期 `visit_date` 为准。该指标具备可加性，各行、各天、各周的结果可直接相加，日粒度汇总求和即可得到更长周期的总停留秒数。

## 取哪张表

统计表的选择取决于统计周期与分析维度。按周期区分，日粒度使用 `_1d` 后缀表，周粒度使用 `_1w` 后缀表，月粒度使用 `_1m` 后缀表。

- 一级品类维度：日用 `dws_traffic_cate1_visit_1d`，周用 `dws_traffic_cate1_visit_1w`，月用 `dws_traffic_cate1_visit_1m`。
- SKU 维度：日用 `dws_traffic_sku_visit_1d`，周用 `dws_traffic_sku_visit_1w`，月用 `dws_traffic_sku_visit_1m`。
- 访问渠道维度：日用 `dws_traffic_vchannel_visit_1d`，周用 `dws_traffic_vchannel_visit_1w`，月用 `dws_traffic_vchannel_visit_1m`。
- 访客维度：日用 `dws_traffic_visitor_visit_1d`，月用 `dws_traffic_visitor_visit_1m`。

同一停留秒数在不同维度汇总表中均有体现，跨维度汇总之间不得相加，以免重复计算。

## 来源与历史变化

`stay_seconds` 来源于访问明细表 `dwd_traffic_visit_di`，上述各汇总表均由该明细表按维度聚合而成。该指标自上线以来未调整计算规则，历史口径与当前口径一致，可直接用于跨期趋势比较。
