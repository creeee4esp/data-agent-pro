# 表说明：dws_trade_prov_refund_1d（DWS-收货省份粒度退款汇总（日））

## 概述

`dws_trade_prov_refund_1d` 按统计日期和收货省份汇总每日退款，当前 906 行。数据来自 `dwd_trade_refund_di`，`refund_cnt` 是退款记录条数，`refund_amount` 是退款金额求和，按退款完成日期归属。比如看某天各省退款，`WHERE stat_date = '2025-07-05'`。

## 字段

- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`，它是退款完成日期。
- `receiver_province`：收货省份，和 `stat_date` 共同唯一，按省份过滤如 `WHERE receiver_province = '江苏省'`。
- `refund_cnt`：退款笔数，当日该省份退款记录条数，可跨天相加，例如 `SUM(refund_cnt)`。
- `refund_amount`：退款金额（元），当日该省份退款金额之和，可跨天相加。

## 上下游

来源是退款明细表 `dwd_trade_refund_di`，按 `refund_date` 和 `receiver_province` 分组聚合写入，当前没有登记下游表。更高层汇总一般直接在本表上再聚合。

## 使用注意

适合做省份的每日退款趋势、日区间退款汇总和省份间退款对比，比如「某日各省退款金额排名」或「某省近 30 天 `SUM(refund_amount)`」。不适合做退款原因、用户维度拆解，那些请改用对应的表。同家族噪声表有备份 `dws_trade_prov_refund_1d_bak`、2 张带日期备份（如 `dws_trade_prov_refund_1d_bak_20250705`、`dws_trade_prov_refund_1d_bak_20251210`）、旧版本 `dws_trade_prov_refund_1d_v2`、测试表 `test_dws_trade_prov_refund_1d`。都是快照、改版遗留或调试产物，数据可能过期或残缺，统计一律用本表。
