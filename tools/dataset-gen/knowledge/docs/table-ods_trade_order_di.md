# 表说明：ods_trade_order_di（ODS-交易订单主表）

## 概述

`ods_trade_order_di` 是 ODS 层的交易订单主表，原样承接业务侧的订单头记录，当前 20,000 行，一行一笔订单。相比业务表，它多了 `etl_time` 和 `dt` 两个技术字段。它是整条交易链路的核心贴源表，下游一大批宽表和汇总都从它展开。举个例子，想看某省当月订单数，`SELECT COUNT(*) FROM ods_trade_order_di WHERE dt = '某分区' AND receiver_province = '浙江省'` 就能拿到。

## 字段

- `order_id`（bigint）：订单 ID，订单的唯一标识，下游关联都靠它。
- `buyer_id`（bigint）：下单用户 ID，关联 `user_info.user_id`，按用户聚合订单用它。
- `order_status`（varchar(20)）：订单状态，created-待支付、cancelled-已取消、paid-已支付、shipped-已发货、completed-已完成、refunded-已退款，算成交要按状态过滤，例如统计已完成订单用 `order_status = 'completed'`。
- `total_amount`（decimal(12,2)）：订单商品总金额，单位元，等于明细金额之和。
- `discount_amt`（decimal(12,2)）：优惠抵扣金额，单位元。
- `pay_amount`（decimal(12,2)）：应付/实付金额，单位元，等于商品总金额减优惠，算 GMV 口径时注意用它还是用 `total_amount`。
- `receiver_province`（varchar(20)）：收货省份，做地域分布用它，例如按 `receiver_province` 分组统计订单数。
- `order_time`（datetime）：下单时间，按下单日期统计用它。
- `pay_time`（datetime）：支付时间，未支付为空，算支付转化或支付日期时要排除空值。
- `etl_time`（datetime）：ETL 同步时间，技术字段，不是业务时间，别拿它做业务口径。
- `dt`（date）：分区日期，查询时用它裁剪分区。

## 上下游

`ods_trade_order_di` 的上游是 `trade_order`，通过 ETL 同步落到 ODS 层。它的下游很多，包括汇总层的 `ads_trade_refund_rate_monthly`、`ads_user_repurchase_yearly`，维度层的 `dim_region`，以及明细层的 `dwd_lgs_shipment_di`、`dwd_trade_order_detail_di`、`dwd_trade_pay_di`、`dwd_trade_refund_di`，这些都靠订单头数据展开。

## 使用注意

`ods_trade_order_di` 适合做订单量、金额、地域分布和订单状态分析，查询时建议带 `dt` 做分区过滤，例如按 `receiver_province` 看各省订单数，或按 `order_status` 看成交结构。算商品级拆解要去订单明细表，别指望主表带明细。库里同家族还有噪声表：备份表 `ods_trade_order_di_bak`、测试表 `test_ods_trade_order_di`。它们是历史快照或调试数据，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_trade_order_di`。
