# 表说明：dws_trade_reason_refund_1w（DWS-退款原因粒度退款汇总（周））

## 概述

`dws_trade_reason_refund_1w` 按周、按退款原因汇总退款，当前 211 行。`stat_date` 存周一日期，一周一行。数据来自 `dwd_trade_refund_di`，`refund_cnt` 是退款记录条数，`refund_amount` 是退款金额求和，按退款完成日期归属。比如看某原因最近 8 周走势，`WHERE refund_reason = '质量问题' ORDER BY stat_date DESC LIMIT 8`。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配，查区间用 `BETWEEN`。
- `refund_reason`：退款原因，和 `stat_date` 共同唯一，同周同原因一行。
- `refund_cnt`：退款笔数，当周该原因退款记录条数，可跨周相加，例如 `SUM(refund_cnt)`。
- `refund_amount`：退款金额（元），当周该原因退款金额之和，可跨周相加。

## 上下游

来源是退款明细表 `dwd_trade_refund_di`，按周一日期和 `refund_reason` 分组聚合写入，当前无下游表。更粗汇总直接在本表上再聚合。

## 使用注意

适合取退款原因的周分布和周区间汇总，比如「近 4 周各退款原因退款金额排名」或「某原因每周退款笔数」。要日粒度用日表，跨月对比用月表。同家族噪声表有历史归档 `dws_trade_reason_refund_1w_his`、备份 `dws_trade_reason_refund_1w_bak`、8 张带日期备份（如 `dws_trade_reason_refund_1w_bak_20250105`、`dws_trade_reason_refund_1w_bak_20250226`），都是归档或快照，数据可能过期，取数只认本表。
