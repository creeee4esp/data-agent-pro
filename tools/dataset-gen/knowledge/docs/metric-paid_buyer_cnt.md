# 指标说明：支付买家数（paid_buyer_cnt）

## 计算公式

`ads_trade_gmv_monthly` 里的支付买家数口径是对已支付订单的买家去重计数，即 `COUNT(DISTINCT IF(is_paid = 1, buyer_id, NULL))`，只挑出 `is_paid = 1` 的明细行取 `buyer_id` 去重，未支付的买家会被置成 NULL 不参与计数。报表按 `DATE_FORMAT(order_date, '%Y-%m')` 聚合到自然月，也就是按下单月归属。因为是去重计数，这个指标不可加和：把各月的支付买家数相加并不等于更长周期的去重买家数，同一个人在多个月都买过会被各月分别计入，要算跨月去重得回到明细重新去重。

## 取哪张表

查支付买家数直接取报表。看月度口径用 `ads_trade_gmv_monthly`，这张表加工时已经按 `DATE_FORMAT(order_date, '%Y-%m')` 分组、按已支付过滤；看日度口径可用 `ads_trade_gmv_daily`。不要自己从明细临时拼去重口径，容易漏掉已支付过滤。

## 来源与历史变化

支付买家数的报表加工回溯到下单明细表 `dwd_trade_order_detail_di`，加工 SQL 为 `INSERT INTO ads_trade_gmv_monthly SELECT DATE_FORMAT(order_date, '%Y-%m'), SUM(IF(is_paid = 1, item_amount, 0)), COUNT(DISTINCT IF(is_paid = 1, order_id, NULL)), COUNT(DISTINCT IF(is_paid = 1, buyer_id, NULL)) FROM dwd_trade_order_detail_di GROUP BY DATE_FORMAT(order_date, '%Y-%m')`。这个指标自上线以来未调整计算规则，去重口径一直没变，历史数据和当前数据可以放心做趋势对比。
