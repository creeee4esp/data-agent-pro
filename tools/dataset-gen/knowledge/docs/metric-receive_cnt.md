# 指标说明：领取数（receive_cnt）

## 计算公式

`ads_coupon_usage_monthly` 里的领取数口径是 `COUNT(*)`，对优惠券领取记录逐行计数，一次领取算一条。报表按 `DATE_FORMAT(r.receive_time, '%Y-%m')` 归到领取月、并按券类型 `c.coupon_type` 分组。它是报表里按月、按券类型加工好的量，取报表值即可，不可加和。举例：想看某类优惠券某月被领了多少张，直接取该月、该券类型那一行的领取数。

## 取哪张表

查领取数只有一张表：月度优惠券核销报表 `ads_coupon_usage_monthly`。举例：要看满减券上月的领取数，查这张表对应月份、券类型为满减的那一行；要看多种券类型的领取对比，取同一月份下各券类型的行即可。不用自己从领取流水临时聚合。

## 来源与历史变化

领取数的报表加工回溯到优惠券领取流水 `ods_mkt_coupon_receive_di`，并关联券维表 `dim_coupon` 取券类型，加工 SQL 为 `INSERT INTO ads_coupon_usage_monthly SELECT DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type, COUNT(*), SUM(r.use_status = 'used'), ROUND(SUM(r.use_status = 'used') / COUNT(*), 4) FROM ods_mkt_coupon_receive_di r JOIN dim_coupon c ON c.coupon_id = r.coupon_id GROUP BY DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type`。这个指标自上线以来未调整计算规则，口径一致，可直接用于跨月对比。
