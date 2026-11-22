# 取数需求单 REQ-2025-0225

## 需求

商品部于 2025-09-21 提出取数，要点如下：

- 指标：退款金额（`refund_amount`）
- 口径：买家用户粒度退款周汇总
- 时间范围：2025-06
- 筛选条件：无
- 单据编号：REQ-2025-0225，处理人流量数据组

## 结论

`dws_trade_user_refund_1w`（DWS-买家用户粒度退款汇总（周））为本次用表，要点如下：

- 取用字段：`stat_date`、`buyer_id`、`refund_cnt`、`refund_amount`
- 报告期：`stat_date` 取 2025-06 内的各周
- 加和说明：`refund_cnt`、`refund_amount` 均可跨行相加，各买家、各周的 `refund_amount` 汇总得到总退款金额口径准确
- 交付情况：已交付，并沉淀为固定报表
