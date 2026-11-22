# 取数需求单 REQ-2025-0241

## 需求

财务部于 2025-10-14 提出取数，需要 2025-07 的退款金额（`refund_amount`），口径为买家用户粒度退款日汇总，无筛选条件。编号 REQ-2025-0241，处理人流量数据组。

## 结论

`dws_trade_user_refund_1d`（DWS-买家用户粒度退款汇总（日））为本次用表，取 `stat_date`、`buyer_id`、`refund_cnt`、`refund_amount`，报告期 `stat_date` 取 2025-07。

加和口径：`refund_cnt`、`refund_amount` 均可跨行相加，各买家、各天的 `refund_amount` 汇总得到月度总退款金额口径准确。

本次已交付一次性取数结果。
