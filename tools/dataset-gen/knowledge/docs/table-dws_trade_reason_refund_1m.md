# 表说明：dws_trade_reason_refund_1m（DWS-退款原因粒度退款汇总（月））

## 概述

`dws_trade_reason_refund_1m` 为退款原因维度的退款月汇总表，当前共 52 行。该表以统计月与退款原因为粒度对退款行为按月聚合，`stat_date` 存储各月月初日期。数据源自退款明细表 `dwd_trade_refund_di`，`refund_cnt` 为退款记录条数，`refund_amount` 为退款金额之和，均按退款完成日期归属。

## 字段

- `stat_date`：统计月，取值为月初日期，代表整月而非某日，按月筛选须以月初日期匹配。
- `refund_reason`：退款原因，与 `stat_date` 共同构成行粒度，同一月同一原因仅一行。
- `refund_cnt`：退款笔数，为当月该原因的退款记录条数，可按月累加。
- `refund_amount`：退款金额（元），为当月该原因退款金额之和，可按月累加。

## 上下游

数据来源为退款明细表 `dwd_trade_refund_di`，加工时按月初日期与 `refund_reason` 分组聚合写入本表。当前无登记在册的下游表，如需更高层级汇总，通常直接基于本表再行聚合。

## 使用注意

该表适用于退款原因的月度分布、月度趋势及原因占比分析。若需日或周粒度数据，应改用对应的日表或周表。该家族含多类噪声表：历史归档 `dws_trade_reason_refund_1m_his`、备份 `dws_trade_reason_refund_1m_bak`、带日期备份 2 张（如 `dws_trade_reason_refund_1m_bak_20250815`、`dws_trade_reason_refund_1m_bak_20250909`）及测试表 `test_dws_trade_reason_refund_1m`。此类表为归档、快照或调试产物，数据可能不完整或已过期，不得用于统计，一切以本表为准。
