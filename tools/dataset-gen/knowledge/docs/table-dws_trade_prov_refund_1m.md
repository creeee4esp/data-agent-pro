# 表说明：dws_trade_prov_refund_1m（DWS-收货省份粒度退款汇总（月））

## 概述

`dws_trade_prov_refund_1m` 为收货省份维度的退款月汇总表，当前共 199 行。该表以统计月与收货省份为粒度对退款行为按月聚合，`stat_date` 存储各月月初日期。数据源自退款明细表 `dwd_trade_refund_di`，`refund_cnt` 为退款记录条数，`refund_amount` 为退款金额之和，均按退款完成日期归属。

## 字段

- `stat_date`：统计月，取值为月初日期，代表整月而非某日，按月筛选须以月初日期匹配。
- `receiver_province`：收货省份，与 `stat_date` 共同构成行粒度，同一月同一省份仅一行。
- `refund_cnt`：退款笔数，为当月该省份的退款记录条数，可按月累加。
- `refund_amount`：退款金额（元），为当月该省份退款金额之和，可按月累加。

## 上下游

数据来源为退款明细表 `dwd_trade_refund_di`，加工时按月初日期与 `receiver_province` 分组聚合写入本表。当前无登记在册的下游表，如需更高层级汇总，通常直接基于本表再行聚合。

## 使用注意

该表适用于省份维度的月度退款趋势、月度排名及同比环比分析。若需日或周粒度数据，应改用对应的日表或周表。该家族含若干噪声表：备份表 `dws_trade_prov_refund_1m_bak` 一张，以及带日期备份 6 张（如 `dws_trade_prov_refund_1m_bak_20250221`、`dws_trade_prov_refund_1m_bak_20250313`）。此类表为历史快照，数据可能不完整或已过期，不得用于统计，一切以本表为准。
