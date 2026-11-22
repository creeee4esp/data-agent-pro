# 表说明：dws_trade_prov_pay_1m（DWS-收货省份粒度支付汇总（月））

## 概述

`dws_trade_prov_pay_1m` 按月、按收货省份汇总支付，当前 193 行。`stat_date` 存月初日期，一月一省一行。数据来自 `dwd_trade_pay_di`，`pay_cnt` 是支付记录条数，`pay_amt` 是金额求和，按支付日期归属。比如看某省全年月度支付走势，`WHERE receiver_province = '浙江省' ORDER BY stat_date`。

## 字段

- `stat_date`：统计月，取月初日期，查某月用该月月初日期匹配。
- `receiver_province`：收货省份，和 `stat_date` 共同唯一，同月同省一行。
- `pay_cnt`：支付笔数，当月该省份支付记录条数，可跨月相加，例如季度 `SUM(pay_cnt)`。
- `pay_amt`：支付金额（元），当月该省份金额之和，可跨月相加。

## 上下游

来源是支付明细表 `dwd_trade_pay_di`，按月初日期和 `receiver_province` 分组聚合写入，当前无下游表。更高层汇总直接在本表上再聚合。

## 使用注意

适合取省份的月度支付笔数金额、月度排名和同比环比，比如「各省某月 `pay_amt` 排名」或「某省各月走势」。要日或周粒度请用对应的日表、周表。同家族噪声表有备份 `dws_trade_prov_pay_1m_bak` 和 2 张带日期备份（如 `dws_trade_prov_pay_1m_bak_20251206`、`dws_trade_prov_pay_1m_bak_20251209`），都是历史快照，数据可能过期，统计只用本表。
