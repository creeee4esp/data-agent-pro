# 取数需求单 REQ-2025-0202

## 需求

运营部 2025-08-25 提单：退款原因粒度退款金额 `refund_amount`（单位：元），按周汇总，区间 2025-04 至 2025-06，无筛选。需长期跟踪，请求固化为报表。

## 结论

`dws_trade_reason_refund_1w` 命中需求。取数字段：`stat_date`、`refund_reason`、`refund_cnt`、`refund_amount`；按 `stat_date` 卡 2025-04 至 2025-06。口径：`refund_cnt`、`refund_amount` 均可加和，按原因或按周汇总直接相加都成立。考虑到长期跟踪，本次已交付，并沉淀为固定报表。
