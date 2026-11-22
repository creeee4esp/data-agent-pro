# 取数需求单 REQ-2025-0218

## 需求

财务部于 2025-09-11 提出取数，需要 2025-07 至 2025-09 的退款笔数（`refund_cnt`），口径为买家用户粒度退款周汇总，无筛选条件。编号 REQ-2025-0218，处理人交易数据组。

## 结论

`dws_trade_user_refund_1w`（DWS-买家用户粒度退款汇总（周））为本次用表，取 `stat_date`、`buyer_id`、`refund_cnt`、`refund_amount`，报告期 `stat_date` 覆盖 2025-07 至 2025-09 的各周。

加和口径：`refund_cnt`、`refund_amount` 均可跨行相加，把多个买家或多周的 `refund_cnt` 累加得到的总退款笔数准确可用。

本次已交付，并沉淀为固定报表。
