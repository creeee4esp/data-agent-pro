# 取数需求单 REQ-2025-0237

## 需求

管理层于 2025-10-08 提出取数，要点如下：

- 指标：退款笔数（`refund_cnt`）
- 口径：退款原因粒度退款月汇总
- 时间范围：2025-10
- 筛选条件：无
- 单据编号：REQ-2025-0237，处理人交易数据组

## 结论

`dws_trade_reason_refund_1m`（DWS-退款原因粒度退款汇总（月））为本次用表，要点如下：

- 取用字段：`stat_date`、`refund_reason`、`refund_cnt`、`refund_amount`
- 报告期：`stat_date` 取 2025-10
- 加和说明：`refund_cnt`、`refund_amount` 均可跨行相加，可按退款原因汇总出当月总退款笔数并排序
- 交付情况：已交付，附 SQL 供需求方自助复用
