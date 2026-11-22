# 表说明：dwd_trade_pay_di（DWD-支付明细）

## 概述

`dwd_trade_pay_di` 是支付的明细表，属 DWD 层，表注释为 DWD-支付明细，当前 18,031 行，一笔支付一行。由 `ods_pay_order_di` 关联 `ods_trade_order_di` 加工，补齐收货省份。是支付渠道、省份、买家维度支付汇总的源头。

## 字段

- `pay_id`：bigint，支付单ID，本表主键。
- `order_id`：bigint，订单ID，关联对应订单。
- `buyer_id`：bigint，买家用户ID。
- `pay_channel`：varchar(20)，支付渠道，按渠道分析支付用它。
- `pay_amt`：decimal(12,2)，支付金额，单位元，可按日期或维度相加。
- `receiver_province`：varchar(20)，收货省份，从订单带入。
- `pay_date`：date，支付日期，业务时间；支付按此日期归属，与 GMV 的下单口径不同。
- `dt`：date，分区日期，用于分区裁剪，勿当支付时间。

## 上下游

- 上游：`ods_pay_order_di`、`ods_trade_order_di`。
- 下游：`ads_pay_channel_monthly`，以及渠道、省份、买家粒度的日/周/月汇总，如 `dws_trade_channel_pay_1d`、`dws_trade_prov_pay_1m`、`dws_trade_user_pay_1w` 等。

## 使用注意

- 适合：支付笔数、支付金额的灵活下钻，按 `pay_channel`、`receiver_province`、`buyer_id` 聚合。
- 建议：固定口径的渠道支付统计直接用 `ads_pay_channel_monthly`。
- 坑点：支付按 `pay_date` 归属，勿与按下单日期的 GMV 混用；区分 `pay_date` 与 `dt`。
- 噪声表：带日期备份 `dwd_trade_pay_di_bak_20250126` 等、旧版本 `dwd_trade_pay_di_old`、测试表 `test_dwd_trade_pay_di`，均为时点副本、历史残留或调试产物，不能用于统计。
- 口径：统计一律用 `dwd_trade_pay_di`。
