# 加工说明：ads_traffic_conversion_daily

## 加工逻辑

`ads_traffic_conversion_daily` 存的是 ADS-每日访问转化，按天汇总，现在一共 365 行，一年里每天一行。它要回答的是"某天有多少人逛、有多少人买、转化率多少"。

加工时先各自聚一层，再拼到一起。访问侧从 `dwd_traffic_visit_di` 按 `visit_date` 分组，用 `COUNT(DISTINCT visitor_id)` 算出当天的 UV；下单侧从 `dwd_trade_order_detail_di` 按 `order_date` 分组，用 `COUNT(DISTINCT buyer_id)` 算出当天的买家数。然后用 `LEFT JOIN` 以访问为主表，按日期对齐，没有订单的那天用 `COALESCE(o.buyers, 0)` 补 0。转化率就是买家数除以 UV，`ROUND(..., 4)` 保留四位小数。

```sql
SELECT v.visit_date, v.uv, COALESCE(o.buyers, 0), ROUND(COALESCE(o.buyers, 0) / v.uv, 4)
FROM (SELECT visit_date, COUNT(DISTINCT visitor_id) AS uv FROM dwd_traffic_visit_di GROUP BY visit_date) v
LEFT JOIN (SELECT order_date, COUNT(DISTINCT buyer_id) AS buyers FROM dwd_trade_order_detail_di GROUP BY order_date) o
  ON o.order_date = v.visit_date
```

跟新同事说一句：这里以访问为主表做左连接，所以哪怕某天没有订单，这一天也不会丢，买家数会显示成 0，转化率算出来也是 0。

## 依赖与调度

`ads_traffic_conversion_daily` 依赖两张上游表：交易订单明细宽表 `dwd_trade_order_detail_di` 和访问明细 `dwd_traffic_visit_di`。调度每天 05:00 开始，等上游全部成功后才运行，处理前一天（T-1）的数据。

## 质量检查

`ads_traffic_conversion_daily` 发布前要过四项检查：主键 / 粒度唯一，保证一天只有一行；关键字段非空；行数与前一天相比波动超过 30% 告警；可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

这几项挂了会直接影响下游。日期粒度重复会让 UV 和买家数被重复计算，转化率失真；行数骤降往往是 `dwd_traffic_visit_di` 没抽全，UV 偏小会把转化率顶虚高。看到告警先把上游确认清楚，再对外发布。
