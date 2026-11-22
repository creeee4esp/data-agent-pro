# 表说明：ods_trade_order_item_di（ODS-交易订单明细表）

## 概述

`ods_trade_order_item_di` 为 ODS 层的交易订单明细贴源表，原样承接业务侧的订单明细记录，当前共 35,138 行。该表以单条订单明细为粒度，一行记录一个订单中某个 SKU 的购买情况，相比业务表增加了 `etl_time` 与 `dt` 两个技术字段。其作为交易链路中最细粒度的贴源数据，是商品销量、明细金额等指标向上加工的基础。

## 字段

- `id`（bigint）：明细 ID，单条订单明细的唯一标识。
- `order_id`（bigint）：订单 ID，用于关联所属订单，按订单聚合明细时以该字段为准。
- `sku_id`（bigint）：商品 SKU ID，标识该明细对应的商品，按商品统计销量时以该字段聚合。
- `qty`（int）：购买件数，汇总销量时以该字段求和。
- `unit_price`（decimal(10,2)）：成交单价，单位元，为该笔明细的实际成交价。
- `item_amount`（decimal(12,2)）：明细金额，单位元，等于件数乘以成交单价，汇总销售额以该字段为准。
- `etl_time`（datetime）：ETL 同步时间，为技术字段，不代表业务发生时间，不应用于业务口径统计。
- `dt`（date）：分区日期，用于分区裁剪。

## 上下游

`ods_trade_order_item_di` 的数据来自上游的 `trade_order_item`，经 ETL 同步进入 ODS 层。其下游为 `dwd_trade_order_detail_di`，由后者在明细层承接订单明细并与其它维度关联加工。

## 使用注意

`ods_trade_order_item_di` 适合用于商品销量、明细金额及按商品聚合的销售分析，查询时建议带 `dt` 做分区过滤。该表仅含明细信息，不含订单状态与收货地域等订单头字段，相关分析需关联订单主表。库内存在若干同家族噪声表：备份表 `ods_trade_order_item_di_bak`、带日期备份 `ods_trade_order_item_di_bak_20250706`、旧版本 `ods_trade_order_item_di_v2` 以及测试表 `test_ods_trade_order_item_di`。上述表分别为历史快照、早期口径或调试用途，其口径与时效不受保证，不得用于正式统计。正式取数一律以 `ods_trade_order_item_di` 为准。
