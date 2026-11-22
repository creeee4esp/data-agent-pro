# 取数需求单 REQ-2025-0205

## 需求

商品部 2025-08-27 提单，想看退款原因维度的退款金额 `refund_amount`（单位：元），按月汇总，报告期就看 2025-01 一个月，不加筛选。主要想拆一下年初各退款原因的金额占比。

## 结论

`dws_trade_reason_refund_1m` 这张表对口，取数用到 `stat_date`、`refund_reason`、`refund_cnt`、`refund_amount` 这几个字段，按 `stat_date` 卡 2025-01 就行。口径上省心：`refund_cnt` 和 `refund_amount` 都是可加和指标，按退款原因汇总直接相加就成立。本次已交付，并附了 SQL 方便商品部自助复用。
