# 取数需求单 REQ-2025-0246

## 需求

运营部于 2025-10-24 提出取数，要点如下：

- 指标：下单数（`order_cnt`）
- 口径：用户粒度交易日汇总
- 时间范围：2025-03 至 2025-05
- 筛选条件：无
- 单据编号：REQ-2025-0246，处理人流量数据组

## 结论

`dws_trade_user_order_1d`（DWS-用户粒度交易汇总（日））为本次用表，要点如下：

- 取用字段：`stat_date`、`buyer_id`、`order_cnt`、`paid_order_cnt`、`paid_amount`
- 报告期：`stat_date` 覆盖 2025-03 至 2025-05
- 关键提醒：`order_cnt` 与 `paid_order_cnt` 均不可跨行相加，不能把各用户、各天的下单数直接求和
- 可加字段：`paid_amount` 可跨行相加
- 交付情况：已交付，附 SQL 供需求方自助复用
