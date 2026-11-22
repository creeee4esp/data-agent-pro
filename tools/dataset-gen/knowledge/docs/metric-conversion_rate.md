# 指标说明：转化率 = 下单用户数 / 访客数（conversion_rate）

## 计算公式

`ads_traffic_conversion_daily` 里的转化率是一个比率，分子是当日下单用户数、分母是当日访客数，口径为 `ROUND(COALESCE(o.buyers, 0) / v.uv, 4)`，按访问日期 `visit_date` 逐日计算。最容易误用的一点是把日转化率直接平均成周或月转化率——比率不可加和，要算更长周期的转化率，必须把分子的下单用户数和分母的访客数各自汇总后再相除，而且访客数去重后不能跨天相加，所以不能简单累加日值。另一个要留意的是分母口径，分母是访客数而非浏览量，别用 `pv` 当分母。

## 取哪张表

查转化率只有一张表：日度转化报表 `ads_traffic_conversion_daily`，按访问日取对应那一行即可。这张表已经把访客数、下单用户数和比率都算好，不要自己用日比率去凑周月。

## 来源与历史变化

转化率的报表加工回溯到访问明细表 `dwd_traffic_visit_di` 与下单明细表 `dwd_trade_order_detail_di`，加工 SQL 为 `INSERT INTO ads_traffic_conversion_daily SELECT v.visit_date, v.uv, COALESCE(o.buyers, 0), ROUND(COALESCE(o.buyers, 0) / v.uv, 4) FROM (SELECT visit_date, COUNT(DISTINCT visitor_id) AS uv FROM dwd_traffic_visit_di GROUP BY visit_date) v LEFT JOIN (SELECT order_date, COUNT(DISTINCT buyer_id) AS buyers FROM dwd_trade_order_detail_di GROUP BY order_date) o ON o.order_date = v.visit_date`。历史变更方面，2025-06-14 由商品数据组发布说明：转化率分母为当日商品详情页访客数。
