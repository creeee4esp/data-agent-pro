# 表说明：dws_trade_channel_pay_1d（DWS-支付渠道粒度支付汇总（日））

## 概述

`dws_trade_channel_pay_1d` 为支付渠道维度的支付日汇总表，当前共 1,459 行。该表以统计日期与支付渠道为粒度，对支付行为按日聚合。数据源自支付明细表 `dwd_trade_pay_di`，`pay_cnt` 为支付记录条数，`pay_amt` 为对应支付金额之和，统计口径按支付日期归属。

## 字段

- `stat_date`：统计日期，粒度为天，按日筛选用等值匹配，按区间筛选用 `BETWEEN`。
- `pay_channel`：支付渠道，与 `stat_date` 共同构成行粒度，同一日同一渠道仅一行。
- `pay_cnt`：支付笔数，为当日该渠道的支付记录条数，可跨日累加。
- `pay_amt`：支付金额（元），为当日该渠道支付金额之和，可跨日累加。

## 上下游

数据来源为支付明细表 `dwd_trade_pay_di`，加工时按 `pay_date` 与 `pay_channel` 分组聚合写入本表。当前无登记在册的下游表，如需更高层级汇总，通常直接基于本表再行聚合。

## 使用注意

该表适用于各支付渠道的每日支付笔数与金额走势、渠道占比以及渠道间对比等分析。因按支付日期归属，与按下单日期归属的下单类口径不同，涉及跨口径对比时须加以区分。该家族含多类噪声表：备份表 `dws_trade_channel_pay_1d_bak`、带日期备份 6 张（如 `dws_trade_channel_pay_1d_bak_20250226`、`dws_trade_channel_pay_1d_bak_20250402`）、旧版本 `dws_trade_channel_pay_1d_v2` 及测试表 `test_dws_trade_channel_pay_1d`。此类表为历史快照、改版遗留或调试产物，数据可能不完整或已过期，不得用于统计，一切以本表为准。
