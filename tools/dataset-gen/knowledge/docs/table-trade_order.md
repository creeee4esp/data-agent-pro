# 表说明：trade_order（交易订单主表）

## 概述

`trade_order` 是 CORE 层的交易订单主表，记录每笔订单的头部信息。

- 粒度：一笔订单一行。
- 当前规模：20,000 行。
- 适用场景：订单量、金额、地域分布与订单状态分析。

## 字段

- `order_id`（bigint）：订单 ID，订单的唯一标识，别的表关联订单都靠它。
- `buyer_id`（bigint）：下单用户 ID，关联 `user_info.user_id`，按用户聚合订单用它。
- `order_status`（varchar(20)）：订单状态，created-待支付、cancelled-已取消、paid-已支付、shipped-已发货、completed-已完成、refunded-已退款，算成交要按状态过滤。
- `total_amount`（decimal(12,2)）：订单商品总金额，单位元，等于明细金额之和。
- `discount_amt`（decimal(12,2)）：优惠抵扣金额，单位元。
- `pay_amount`（decimal(12,2)）：应付/实付金额，单位元，等于商品总金额减优惠，算 GMV 口径要注意用它还是 `total_amount`。
- `receiver_province`（varchar(20)）：收货省份，做地域分布用它。
- `order_time`（datetime）：下单时间，按下单日期统计用它。
- `pay_time`（datetime）：支付时间，未支付为空，算支付转化或支付日期要排除空值。

## 上下游

`trade_order` 的上游是业务系统（在线数据库）。

- 下游：ODS 层的 `ods_trade_order_di`，由它承接再往上层加工。
- 关联：`pay_order.order_id`、`pay_refund.order_id`、`trade_order_item.order_id` 均指向 `trade_order.order_id`；`trade_order.buyer_id` 指向 `user_info.user_id`。

## 使用注意

`trade_order` 适合做订单量、金额、地域分布和订单状态分析。

- 不适合：商品级明细拆解，那要去订单明细表。
- 噪声表：备份表 `trade_order_bak`、带日期备份如 `trade_order_bak_20250310`、日分表（`trade_order_20250101 ～ trade_order_20251231`，如 `trade_order_20250101`）、月分表（`trade_order_202301 ～ trade_order_202512`，如 `trade_order_202301`）。
- 为什么不能用：历史快照或按期拆分存档，口径与完整性都不保证。
- 正式取数只认 `trade_order`。
