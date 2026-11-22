# 指标说明：使用数（used_cnt）

## 计算公式

`ads_coupon_usage_monthly` 中的使用数定义为一段时间内被核销使用的优惠券数量，计算口径为 `SUM(r.use_status = 'used')`，即对领取记录中使用状态为 `used` 的条目计数。报表按 `DATE_FORMAT(r.receive_time, '%Y-%m')` 归属到优惠券领取月，并按券类型 `c.coupon_type` 分组。该指标为报表按月、按券类型加工所得，使用时以报表值为准，不作跨维度叠加。

## 取哪张表

统计表仅有一张：月度优惠券核销报表 `ads_coupon_usage_monthly`。查询时按领取月与券类型定位对应记录即可获得使用数，不同券类型之间的使用数不得相加为同一口径。

## 来源与历史变化

使用数的报表加工回溯至优惠券领取流水 `ods_mkt_coupon_receive_di`，并关联券维表 `dim_coupon` 获取券类型，加工 SQL 为 `INSERT INTO ads_coupon_usage_monthly SELECT DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type, COUNT(*), SUM(r.use_status = 'used'), ROUND(SUM(r.use_status = 'used') / COUNT(*), 4) FROM ods_mkt_coupon_receive_di r JOIN dim_coupon c ON c.coupon_id = r.coupon_id GROUP BY DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type`。该指标自上线以来未调整计算规则，历史口径与当前口径一致，可直接用于跨期趋势比较。
