# 取数需求单 REQ-2025-0066

## 需求

市场部于 2025-03-19 提出取数需求 REQ-2025-0066，要退款原因粒度的退款金额 `refund_amount`（单位元），口径按月，时间范围 2025-02，无筛选条件。诉求是看当月各退款原因的退款金额分布。

## 结论

`dws_trade_reason_refund_1m` 为本次用表，由交易数据组处理交付。维度取 `stat_date`、`refund_reason`，指标输出 `refund_amount` 退款金额。可加性提示：`refund_cnt`、`refund_amount` 均可跨行相加，按退款原因或按月汇总可直接 SUM。本需求已交付，并沉淀为固定报表。
