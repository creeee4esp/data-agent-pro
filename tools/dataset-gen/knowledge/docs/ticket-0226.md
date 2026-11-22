# 取数需求单 REQ-2025-0226

## 需求

会员中心于 2025-09-23 提出取数，需要 2025-01 至 2025-03 的已支付订单明细金额（`paid_amount`），口径为一级品类粒度下单日汇总，无筛选条件。编号 REQ-2025-0226，处理人交易数据组。

## 结论

`dws_trade_cate1_order_1d`（DWS-一级品类粒度下单汇总（日））为本次用表，取 `stat_date`、`category1_id`、`category1_name`、`order_cnt`、`item_qty`、`paid_amount`，报告期 `stat_date` 覆盖 2025-01 至 2025-03。

加和口径：`paid_amount`、`item_qty` 可跨行相加，汇总用这两个字段；`order_cnt` 不可跨行相加，不要逐日求和。

本次已交付一次性取数结果。
