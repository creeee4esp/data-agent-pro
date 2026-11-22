# 表说明：ods_pay_order_di（ODS-支付流水表）

## 概述

`ods_pay_order_di` 为 ODS 层的支付流水贴源表，原样承接业务侧的支付成功流水，当前共 18,031 行。该表以单笔支付为粒度，一行记录一次支付，相比业务表增加了 `etl_time` 与 `dt` 两个技术字段。其作为交易支付链路的贴源数据，是支付金额、支付渠道等指标向上加工的起点。

## 字段

- `pay_id`（bigint）：支付单 ID，单笔支付的唯一标识。
- `order_id`（bigint）：订单 ID，用于关联订单，统计时以该字段对应订单。
- `uid`（bigint）：付款用户 ID，即 `user_info.user_id`，按用户聚合支付时以该字段为准。
- `pay_channel`（varchar(20)）：支付渠道，取值为 alipay-支付宝、wechat-微信、card-银行卡、unionpay-云闪付，按渠道统计应使用对应枚举值。
- `pay_amt`（decimal(12,2)）：支付金额，单位元，等于订单实付金额，汇总支付额以该字段为准。
- `pay_status`（varchar(10)）：支付状态，取值为 success-成功，按成功支付统计时以该状态过滤。
- `pay_time`（datetime）：支付完成时间，按支付日期统计时应采用该业务时间字段。
- `etl_time`（datetime）：ETL 同步时间，为技术字段，不代表业务发生时间，不应用于业务口径统计。
- `dt`（date）：分区日期，用于分区裁剪。

## 上下游

`ods_pay_order_di` 的数据来自上游的 `pay_order`，经 ETL 同步进入 ODS 层。其下游为 `dwd_trade_pay_di`，由后者在明细层承接支付数据并继续加工。

## 使用注意

`ods_pay_order_di` 适合用于支付金额汇总、支付渠道分布及按用户或订单的支付分析，查询时建议带 `dt` 做分区过滤。该表不适合用于退款或订单商品明细类统计，相关指标应取对应链路的表。库内存在若干同家族噪声表：带日期备份 `ods_pay_order_di_bak_20250221`、`ods_pay_order_di_bak_20250601` 以及旧版本 `ods_pay_order_di_v2`。上述表分别为历史快照与早期口径，其口径与时效不受保证，不得用于正式统计。正式取数一律以 `ods_pay_order_di` 为准。
