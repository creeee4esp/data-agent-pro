# 指标说明：访客数（uv）

## 计算公式

`uv` 定义为一段时间内的去重访客数，计算口径为 `COUNT(DISTINCT visitor_id)`，即对访客标识去重后计数。时间归属以访问日期 `visit_date` 为准。该指标为去重类指标，不具备可加性：同一访客在不同日期均会被各自当日去重，若将各天的 `uv` 直接相加，会把跨天重复访问的同一访客多次计入，所得结果并非真实的去重访客数。因此，若需更长周期的访客数，应回到对应粒度重新按 `visitor_id` 去重，不能用日粒度结果累加。

## 取哪张表

统计表的选择取决于统计周期与分析维度。按周期区分，日粒度使用 `_1d` 后缀表，周粒度使用 `_1w` 后缀表，月粒度使用 `_1m` 后缀表。

- 一级品类维度：日用 `dws_traffic_cate1_visit_1d`，周用 `dws_traffic_cate1_visit_1w`，月用 `dws_traffic_cate1_visit_1m`。
- SKU 维度：日用 `dws_traffic_sku_visit_1d`，周用 `dws_traffic_sku_visit_1w`，月用 `dws_traffic_sku_visit_1m`。
- 访问渠道维度：日用 `dws_traffic_vchannel_visit_1d`，周用 `dws_traffic_vchannel_visit_1w`，月用 `dws_traffic_vchannel_visit_1m`。
- 访客维度：日用 `dws_traffic_visitor_visit_1d`，月用 `dws_traffic_visitor_visit_1m`。

若需按日查看转化口径下的访客数，可使用日度报表 `ads_traffic_conversion_daily`。

## 来源与历史变化

`uv` 来源于访问明细表 `dwd_traffic_visit_di`，上述各汇总表与报表均由该明细表按维度聚合而成。历史变更方面，2025-10-17 由交易数据组发布说明：`uv` 为去重访客数，不能跨天相加。进行跨周期统计时须严格遵循该口径。
