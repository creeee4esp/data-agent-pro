# 表说明：trade_order_item（交易订单明细表）

## 概述

`trade_order_item` 是 CORE 层的交易订单明细表，记录每笔订单里买了哪些商品、各买多少，当前 35,138 行，一行对应订单中的一个 SKU。它是交易链路里最细的一层，想算商品销量、明细金额，或者把评价、售后追回到具体是哪件商品，都离不开它。配合订单主表一起用，才能既有订单头又有商品明细。

## 字段

- `id`（bigint）：明细 ID，单条明细的唯一标识，评价和售后表都指向它。
- `order_id`（bigint）：订单 ID，想把明细挂回订单头就靠它关联。
- `sku_id`（bigint）：商品 SKU ID，按商品统计销量、关联商品属性用它。
- `qty`（int）：购买件数，算销量就对它求和。
- `unit_price`（decimal(10,2)）：成交单价，单位元，是这笔明细的实际成交价，算历史客单价要用它而不是商品当前售价。
- `item_amount`（decimal(12,2)）：明细金额，单位元，等于件数乘以成交单价，汇总销售额以它为准。

## 上下游

`trade_order_item` 的上游是业务系统（在线数据库），同步后进入数据平台，下游是 ODS 层的 `ods_trade_order_item_di`，由它承接再往上层加工。表内关联较多：`aftersale_order.order_item_id` 和 `item_review.order_item_id` 都指向 `trade_order_item.id`，`trade_order_item.order_id` 指向 `trade_order.order_id`，`trade_order_item.sku_id` 指向 `item_sku.sku_id`。

## 使用注意

`trade_order_item` 适合做商品销量、明细金额汇总和按商品的销售分析，也适合作为评价、售后回溯到商品的桥梁。它只有明细字段，没有订单状态和收货地域，那些要关联订单主表才拿得到。库里同家族还有噪声表：带日期备份如 `trade_order_item_bak_20250403`、`trade_order_item_bak_20251020`，以及测试表 `test_trade_order_item`。它们是历史快照或调试数据，口径和时效都不保证，不能拿来跑统计，正式取数只认 `trade_order_item`。
