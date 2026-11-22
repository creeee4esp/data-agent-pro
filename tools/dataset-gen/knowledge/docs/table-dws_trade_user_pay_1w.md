# 表说明：dws_trade_user_pay_1w（DWS-买家用户粒度支付汇总（周））

## 概述

`dws_trade_user_pay_1w` 按统计周和买家用户汇总支付。

- 当前 14,350 行。
- `stat_date` 存周一日期，一周一行。
- 来源 `dwd_trade_pay_di`，按支付日期归属。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配。
- `buyer_id`：买家用户ID，`bigint`，和 `stat_date` 共同唯一，是用户维度主键。
- `pay_cnt`：支付笔数，当周该用户支付记录条数，可跨周相加。
- `pay_amt`：支付金额（元），当周该用户金额之和，可跨周相加。

## 上下游

- 来源：支付明细表 `dwd_trade_pay_di`。
- 加工：按周一日期和 `buyer_id` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：用户级周支付、周活付费用户数（`COUNT(DISTINCT buyer_id)`）、人均周支付等。
- 注意：按支付日期归属；`buyer_id` 跨周不可直接相加去重。
- 不适合：日粒度用日表，跨月对比用月表。
- 同家族噪声表：历史归档 `dws_trade_user_pay_1w_his`、备份 `dws_trade_user_pay_1w_bak`、6 张带日期备份（如 `dws_trade_user_pay_1w_bak_20250118`、`dws_trade_user_pay_1w_bak_20250315`）、测试表 `test_dws_trade_user_pay_1w`。
- 这些是归档、快照或调试产物，数据可能过期或残缺，统计一律用本表。
