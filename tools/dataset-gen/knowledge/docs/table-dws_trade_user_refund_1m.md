# 表说明：dws_trade_user_refund_1m（DWS-买家用户粒度退款汇总（月））

## 概述

`dws_trade_user_refund_1m` 是用户维度的退款月汇总表，当前 976 行。先说清楚两点：`stat_date` 存月初日期代表整月，不是某天；表里只有发生过退款的用户，不是全量用户。数据从退款明细 `dwd_trade_refund_di` 来，`refund_cnt` 是退款记录条数，`refund_amount` 是退款金额求和，按退款完成日期归属。

## 字段

- `stat_date`：统计月，取月初日期，按月筛选时用月初日期去匹配。
- `buyer_id`：买家用户ID，`bigint` 类型，和 `stat_date` 一起决定一行，是用户维度主键。
- `refund_cnt`：退款笔数，当月该用户的退款记录条数，可以跨月相加。
- `refund_amount`：退款金额（元），当月该用户退款金额之和，可以跨月相加。

## 上下游

来源是退款明细表 `dwd_trade_refund_di`，加工时按月初日期和 `buyer_id` 分组聚合写入本表，目前没有下游表。需要更粗维度的退款汇总时走对应维度的表。

## 使用注意

想看用户级的月度退款、识别高退款用户、或做月度退款走势，用这张表合适，一月一个用户一行。但记住它只含有退款的用户，算退款率时分母要另找；`buyer_id` 跨月也不能直接去重相加，区间退款用户数要用 `COUNT(DISTINCT buyer_id)` 重新去重。另外同家族里有一批噪声表：备份 `dws_trade_user_refund_1m_bak` 和 8 张带日期备份（如 `dws_trade_user_refund_1m_bak_20250303`、`dws_trade_user_refund_1m_bak_20250308`），都是某次操作前的历史快照，数据未必最新，做统计时一律认准本表，别取到备份上。
