# 取数需求单 REQ-2025-0173

## 需求

客服部 2025-07-26 提单：退款原因粒度退款金额 `refund_amount`（单位：元），按日汇总，区间 2025-05 至 2025-06，无筛选。

## 结论

`dws_trade_reason_refund_1d` 命中需求。取数字段：`stat_date`、`refund_reason`、`refund_cnt`、`refund_amount`；按 `stat_date` 卡 2025-05 至 2025-06。口径：`refund_cnt`、`refund_amount` 均可加和，按原因或按日汇总直接相加都成立。本次已交付一次性取数结果。
