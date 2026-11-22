# 取数需求单 REQ-2025-0274

## 需求

会员中心于 2025-11-20 提出取数，需要 2025-03 至 2025-04 的已支付订单明细金额（`paid_amount`），口径为品牌粒度下单日汇总，无筛选条件。编号 REQ-2025-0274，处理人流量数据组。

## 结论

`dws_trade_brand_order_1d`（DWS-品牌粒度下单汇总（日））为本次用表，取 `stat_date`、`brand_name`、`order_cnt`、`item_qty`、`paid_amount`，报告期 `stat_date` 覆盖 2025-03 至 2025-04。

加和口径：`paid_amount`、`item_qty` 可跨行相加，汇总金额与件数用这两个字段；`order_cnt` 不可跨行相加，涉及下单数不要逐日求和。

本次已交付一次性取数结果。
