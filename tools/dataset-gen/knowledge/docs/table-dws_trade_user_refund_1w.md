# 表说明：dws_trade_user_refund_1w（DWS-买家用户粒度退款汇总（周））

## 概述

`dws_trade_user_refund_1w` 为买家用户维度的退款周汇总表，当前共 1,018 行。该表以统计周与买家用户为粒度对退款行为按周聚合，`stat_date` 存储每周周一日期。数据源自退款明细表 `dwd_trade_refund_di`，`refund_cnt` 为退款记录条数，`refund_amount` 为退款金额之和，均按退款完成日期归属；表中仅包含发生过退款的用户，并非全量用户。

## 字段

- `stat_date`：统计周，取值为周一日期，按周筛选须以该周周一日期匹配，按区间筛选使用 `BETWEEN`。
- `buyer_id`：买家用户ID，类型为 `bigint`，与 `stat_date` 共同构成行粒度，为用户维度主键。
- `refund_cnt`：退款笔数，为当周该用户的退款记录条数，可按周累加。
- `refund_amount`：退款金额（元），为当周该用户退款金额之和，可按周累加。

## 上下游

数据来源为退款明细表 `dwd_trade_refund_di`，加工时按周一日期与 `buyer_id` 分组聚合写入本表。当前无登记在册的下游表，如需更粗维度的退款汇总，应使用对应维度的汇总表。

## 使用注意

该表适用于用户维度的周退款分析、高退款用户识别及周区间退款汇总。须注意两点：其一，表中仅含有退款的用户，计算退款率时分母应另行取自下单或支付用户数；其二，`buyer_id` 跨周不可直接累加去重，统计区间退款用户数须使用 `COUNT(DISTINCT buyer_id)`。该家族含备份表 `dws_trade_user_refund_1w_bak` 及带日期备份 5 张（如 `dws_trade_user_refund_1w_bak_20250111`、`dws_trade_user_refund_1w_bak_20250322`），此类表为历史快照，数据可能已过期，不得用于统计，一切以本表为准。
