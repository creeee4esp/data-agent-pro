# 指标说明：下单用户数（buyer_cnt）

## 计算公式

`ads_traffic_conversion_daily` 里的下单用户数是当日下单买家的去重数。

- 口径：对下单明细按 `order_date` 分组取 `COUNT(DISTINCT buyer_id)`，报表中记为 `buyers`，并用 `COALESCE(o.buyers, 0)` 兜底为 0。
- 时间归属：按下单日期归属，并与访问日期 `visit_date` 对齐关联。
- 可加和：否。去重买家数不能跨天相加，跨天重复下单的买家会被多次计入。

## 取哪张表

查下单用户数按周期选表。

- 日度口径：`ads_traffic_conversion_daily`。
- 年度复购口径：`ads_user_repurchase_yearly`。

## 来源与历史变化

下单用户数的报表加工回溯到下单明细表 `dwd_trade_order_detail_di` 与访问明细表 `dwd_traffic_visit_di`。

- 加工 SQL：`INSERT INTO ads_traffic_conversion_daily SELECT v.visit_date, v.uv, COALESCE(o.buyers, 0), ROUND(COALESCE(o.buyers, 0) / v.uv, 4) FROM (SELECT visit_date, COUNT(DISTINCT visitor_id) AS uv FROM dwd_traffic_visit_di GROUP BY visit_date) v LEFT JOIN (SELECT order_date, COUNT(DISTINCT buyer_id) AS buyers FROM dwd_trade_order_detail_di GROUP BY order_date) o ON o.order_date = v.visit_date`。
- 历史变更：自上线以来未调整计算规则。
