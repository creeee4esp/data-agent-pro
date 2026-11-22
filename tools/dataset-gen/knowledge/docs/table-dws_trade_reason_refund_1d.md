# 表说明：dws_trade_reason_refund_1d（DWS-退款原因粒度退款汇总（日））

## 概述

`dws_trade_reason_refund_1d` 按统计日期和退款原因汇总每日退款。

- 当前 736 行。
- 来源 `dwd_trade_refund_di`，按退款完成日期归属。
- `refund_cnt` 退款记录条数，`refund_amount` 退款金额求和。

## 字段

- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`，它是退款完成日期。
- `refund_reason`：退款原因，和 `stat_date` 共同唯一，同日同原因一行。
- `refund_cnt`：退款笔数，当日该原因的退款记录条数，可跨天相加。
- `refund_amount`：退款金额（元），当日该原因退款金额之和，可跨天相加。

## 上下游

- 来源：退款明细表 `dwd_trade_refund_di`。
- 加工：按 `refund_date` 和 `refund_reason` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：退款原因的每日分布、日区间汇总、原因间对比（如某日各原因退款笔数排名）。
- 不适合：省份、用户维度拆解，请用对应的表。
- 同家族噪声表：备份 `dws_trade_reason_refund_1d_bak`、6 张带日期备份（如 `dws_trade_reason_refund_1d_bak_20250116`、`dws_trade_reason_refund_1d_bak_20250301`）、旧版本 `dws_trade_reason_refund_1d_v1`。
- 这些是快照或改版遗留，数据可能过期，统计一律用本表。
