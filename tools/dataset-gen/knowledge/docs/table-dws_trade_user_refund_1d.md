# 表说明：dws_trade_user_refund_1d（DWS-买家用户粒度退款汇总（日））

## 概述

`dws_trade_user_refund_1d` 按统计日期和买家用户汇总每日退款，当前 1,031 行。数据来自 `dwd_trade_refund_di`，`refund_cnt` 是退款记录条数，`refund_amount` 是退款金额求和，按退款完成日期归属。这里要特别点一句：它只覆盖发生过退款的用户和日期，不是全量用户，别拿它当用户基数去算退款率。

## 字段

- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`，它是退款完成日期。
- `buyer_id`：买家用户ID，`bigint`，和 `stat_date` 共同唯一，是用户维度主键。
- `refund_cnt`：退款笔数，当日该用户的退款记录条数，可跨天相加。
- `refund_amount`：退款金额（元），当日该用户退款金额之和，可跨天相加。

## 上下游

来源是退款明细表 `dwd_trade_refund_di`，按 `refund_date` 和 `buyer_id` 分组聚合写入，当前没有登记下游表。需要更粗维度的退款汇总时走对应维度的表。

## 使用注意

适合做用户级的每日退款分析、高退款用户识别、日区间退款汇总，比如「某日退款金额最高的用户」。要注意的坑有两个：一是它只含有退款的用户，算退款率时分母要另找下单或支付用户数；二是 `buyer_id` 跨天不能直接去重相加，区间退款用户数要用 `COUNT(DISTINCT buyer_id)`。同家族有备份 `dws_trade_user_refund_1d_bak` 和 4 张带日期备份（如 `dws_trade_user_refund_1d_bak_20250212`、`dws_trade_user_refund_1d_bak_20250221`），都是历史快照，数据可能过期，统计一律用本表。
