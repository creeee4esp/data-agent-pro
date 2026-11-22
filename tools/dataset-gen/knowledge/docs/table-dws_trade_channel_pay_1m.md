# 表说明：dws_trade_channel_pay_1m（DWS-支付渠道粒度支付汇总（月））

## 概述

`dws_trade_channel_pay_1m` 按月、按支付渠道汇总支付情况，当前 49 行，数据量很小。`stat_date` 存每月月初日期，一月一行。数据来自 `dwd_trade_pay_di`，`pay_cnt` 是支付记录条数，`pay_amt` 是支付金额求和，按支付日期归属。比如看某渠道全年月度走势，`WHERE pay_channel = '支付宝' ORDER BY stat_date`。

## 字段

- `stat_date`：统计月，取月初日期，查某月用该月月初日期匹配。
- `pay_channel`：支付渠道，和 `stat_date` 共同唯一，同月同渠道一行。
- `pay_cnt`：支付笔数，当月该渠道的支付记录条数，可跨月相加，例如季度 `SUM(pay_cnt)`。
- `pay_amt`：支付金额（元），当月该渠道金额之和，可跨月相加。

## 上下游

来源是支付明细表 `dwd_trade_pay_di`，按月初日期和 `pay_channel` 分组聚合写入，当前无下游表。更高层汇总直接在本表上再聚合。

## 使用注意

适合取渠道的月度支付笔数金额、月度占比和同比环比，比如「各渠道某月 `pay_amt` 占比」或「某渠道各月走势」。要日或周粒度请用对应的日表、周表。同家族噪声表有备份 `dws_trade_channel_pay_1m_bak`、4 张带日期备份（如 `dws_trade_channel_pay_1m_bak_20250423`、`dws_trade_channel_pay_1m_bak_20250807`）和旧版本 `dws_trade_channel_pay_1m_v1`，都是历史快照或改版遗留，数据可能过期，统计只用本表。
