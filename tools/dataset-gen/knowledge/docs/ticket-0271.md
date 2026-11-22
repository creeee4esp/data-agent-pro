# 取数需求单 REQ-2025-0271

## 需求

市场部于 2025-11-16 提出取数，需要 2025-11 的下单数（`order_cnt`），口径为买家用户粒度下单月汇总，无筛选条件。编号 REQ-2025-0271，处理人交易数据组。

## 结论

`dws_trade_user_order_1m`（DWS-买家用户粒度下单汇总（月））为本次用表，取 `stat_date`、`buyer_id`、`order_cnt`、`item_qty`、`paid_amount`，报告期 `stat_date` 取 2025-11。

加和口径：`order_cnt` 不可跨行相加，不要把各买家的 `order_cnt` 求和当合计；`item_qty`、`paid_amount` 可跨行相加，汇总用这两个字段。

本次已交付，附 SQL 供需求方自助复用。
