# 取数需求单 REQ-2025-0136

## 需求

物流部想拉一份 2025-01 至 2025-03、按商品 SKU 粒度的日度下单数 `order_cnt`，不加筛选，用来结合单品成交看看哪些货配送压力大。

## 结论

`dws_trade_sku_order_1d` 是商品 SKU 粒度的按日下单汇总表，拿它来做刚好。字段取 `stat_date`、`sku_id`、`order_cnt`、`item_qty`、`paid_amount`，按 `stat_date` 卡在 2025-01 到 2025-03。

有个口径得提前说清楚：下单数 `order_cnt` 不能跨行相加。想看某个 SKU 这一季度一共下了多少单，不能把各天的 `order_cnt` 直接加起来，会重复算；要排货品量级，建议用可加的 `item_qty` 或 `paid_amount`，或者按天逐行看。

这次已经交付，并附上了 SQL，物流部后面想换 SKU 或时间段可以自己复用。
