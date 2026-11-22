# 表说明：dws_trade_user_pay_1m（DWS-买家用户粒度支付汇总（月））

## 概述

`dws_trade_user_pay_1m` 按统计月和买家用户汇总支付。

- 当前 8,812 行。
- `stat_date` 存月初日期，代表整月。
- 来源 `dwd_trade_pay_di`，按支付日期归属。

## 字段

- `stat_date`：统计月，取月初日期，按月筛选用月初日期匹配。
- `buyer_id`：买家用户ID，`bigint`，和 `stat_date` 共同唯一，是用户维度主键。
- `pay_cnt`：支付笔数，当月该用户支付记录条数，可跨月相加。
- `pay_amt`：支付金额（元），当月该用户金额之和，可跨月相加。

## 上下游

- 来源：支付明细表 `dwd_trade_pay_di`。
- 加工：按月初日期和 `buyer_id` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：用户级月支付、月活付费用户数（`COUNT(DISTINCT buyer_id)`）、人均月支付等。
- 注意：按支付日期归属；`buyer_id` 跨月不可直接相加去重。
- 不适合：日或周粒度用对应日表、周表。
- 同家族噪声表：备份 `dws_trade_user_pay_1m_bak`、8 张带日期备份（如 `dws_trade_user_pay_1m_bak_20250304`、`dws_trade_user_pay_1m_bak_20250405`）、测试表 `test_dws_trade_user_pay_1m`。
- 这些是快照或调试产物，数据可能过期或残缺，统计一律用本表。
