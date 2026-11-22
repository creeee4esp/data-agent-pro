# 取数需求单 REQ-2025-0283

## 需求

商品部 2025-12-06 提的，想要 2025-05 至 2025-06 的下单数（`order_cnt`），按会员等级粒度、周度下单口径拉，没别的筛选。单号 REQ-2025-0283，用户数据组接的。

## 结论

`dws_trade_level_order_1w`（DWS-会员等级粒度下单汇总（周））这张表够用，字段用 `stat_date`、`member_level`、`order_cnt`、`item_qty`、`paid_amount`，日期 `stat_date` 卡在 2025-05 到 2025-06 的各周。

加和这块要提醒：`order_cnt` 不可跨行相加，别把各会员等级或各周的 `order_cnt` 加起来当合计，会重复计数。想加和用 `item_qty` 和 `paid_amount`，这两个跨行相加没问题。

这次已交付，附 SQL 供需求方自助复用。
