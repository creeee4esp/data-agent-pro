# 取数需求单 REQ-2025-0214

## 需求

物流部于 2025-09-06 提出一次性取数，想要 2025-01 的下单数（`order_cnt`），按商品 SKU 粒度的月度汇总口径查看，无额外筛选条件。编号 REQ-2025-0214，处理人数据平台组。

## 结论

`dws_trade_sku_order_1m` 即 DWS-商品SKU 粒度下单汇总（月）是本次取数的用表，取 `stat_date`、`sku_id`、`order_cnt`、`item_qty`、`paid_amount` 几个字段，报告期 `stat_date` 取 2025-01。

需要提醒一点：`order_cnt` 不可跨行相加，把多个 SKU 的 `order_cnt` 直接求和会重复计数，只能逐行查看或配合去重口径。相比之下 `item_qty`、`paid_amount` 可以跨行相加，做小计和总计时用这两个字段。

本次已交付一次性取数结果。
