# 指标说明：退款率 = 退款订单数 / 支付订单数（refund_rate）

## 计算公式

`ads_trade_refund_rate_monthly` 里的退款率是一个比率，分子为退款订单数、分母为支付订单数，口径为 `ROUND(SUM(IF(o.order_status = 'refunded', 1, 0)) / COUNT(*), 4)`，统计范围限定 `o.pay_time IS NOT NULL`，按 `DATE_FORMAT(o.order_time, '%Y-%m')` 归到下单月。最容易误用的一点是把各月退款率直接平均或相加——比率不可加和，要算一个季度或更长周期的退款率，必须把分子分母各自汇总后再相除，不能对月度比率取平均。另一个坑是分母口径：分母是该月下单且已支付的订单数，不是全部下单数，别拿未支付订单进去稀释分母。

## 取哪张表

查退款率只有一张表：月度退款率报表 `ads_trade_refund_rate_monthly`，按月查看对应月份那一行即可。这张表已经把分子分母和比率都算好，不要自己用月度比率去凑更长周期。

## 来源与历史变化

退款率的报表加工回溯到订单表 `ods_trade_order_di`，加工 SQL 为 `INSERT INTO ads_trade_refund_rate_monthly SELECT DATE_FORMAT(o.order_time, '%Y-%m'), COUNT(*), SUM(IF(o.order_status = 'refunded', 1, 0)), ROUND(SUM(IF(o.order_status = 'refunded', 1, 0)) / COUNT(*), 4) FROM ods_trade_order_di o WHERE o.pay_time IS NOT NULL GROUP BY DATE_FORMAT(o.order_time, '%Y-%m')`。历史变更方面，2025-12-08 由物流数据组发布说明：退款率按下单月归属，分母为该月下单且已支付的订单数。
