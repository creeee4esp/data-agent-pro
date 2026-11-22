# 指标说明：核销率（use_rate）

## 计算公式

`ads_coupon_usage_monthly` 里的核销率是一个比率，分子是使用数、分母是领取数，口径为 `ROUND(SUM(r.use_status = 'used') / COUNT(*), 4)`。拆开看就是：把使用状态为 `used` 的领取记录计为分子，除以当期全部领取记录数 `COUNT(*)`，保留四位小数。报表按 `DATE_FORMAT(r.receive_time, '%Y-%m')` 归到领取月、并按券类型 `c.coupon_type` 分组。要提醒新同事的是，比率不可加和，想看更长周期的核销率得把使用数和领取数各自汇总后再相除，不能对各月核销率取平均。

## 取哪张表

查核销率就用月度优惠券核销报表 `ads_coupon_usage_monthly`，按领取月和券类型取对应那一行即可。这张表已经把领取数、使用数和核销率都算好，不用自己从领取流水临时拼比率。

## 来源与历史变化

核销率的报表加工一路回溯到优惠券领取流水 `ods_mkt_coupon_receive_di`，并关联券维表 `dim_coupon` 取券类型，加工 SQL 为 `INSERT INTO ads_coupon_usage_monthly SELECT DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type, COUNT(*), SUM(r.use_status = 'used'), ROUND(SUM(r.use_status = 'used') / COUNT(*), 4) FROM ods_mkt_coupon_receive_di r JOIN dim_coupon c ON c.coupon_id = r.coupon_id GROUP BY DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type`。历史变更方面，2025-12-15 由交易数据组发布说明：核销率按优惠券领取月归属。
