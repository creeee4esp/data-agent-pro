# 指标说明：退款订单数（refund_order_cnt）

## 计算公式

`ads_trade_refund_rate_monthly` 里的退款订单数，口径是 `SUM(IF(o.order_status = 'refunded', 1, 0))`，即在已支付订单里把订单状态为 `refunded` 的记 1 累加。统计范围限定 `o.pay_time IS NOT NULL`，也就是只看支付过的订单。时间归属按 `DATE_FORMAT(o.order_time, '%Y-%m')` 归到下单月。它是报表里按月加工好的量，不可加和，取月报里的值即可。举例：想看某月有多少已支付订单最终退款，直接取该月这一列。

## 取哪张表

查退款订单数只有一张表：月度退款率报表 `ads_trade_refund_rate_monthly`。举例：要看 2025 年某月的退款订单数，直接查这张月报对应月份那一行，不用自己从订单表临时算。

## 来源与历史变化

退款订单数的报表加工回溯到订单表 `ods_trade_order_di`，加工 SQL 为 `INSERT INTO ads_trade_refund_rate_monthly SELECT DATE_FORMAT(o.order_time, '%Y-%m'), COUNT(*), SUM(IF(o.order_status = 'refunded', 1, 0)), ROUND(SUM(IF(o.order_status = 'refunded', 1, 0)) / COUNT(*), 4) FROM ods_trade_order_di o WHERE o.pay_time IS NOT NULL GROUP BY DATE_FORMAT(o.order_time, '%Y-%m')`。这个指标自上线以来未调整计算规则，口径一致，可直接用于跨月对比。
