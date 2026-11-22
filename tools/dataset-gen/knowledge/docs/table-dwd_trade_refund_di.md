# 表说明：dwd_trade_refund_di（DWD-退款明细）

## 概述

`dwd_trade_refund_di` 是退款的明细表，属于 DWD 层，表注释为 DWD-退款明细，当前 1,031 行，一笔退款一行。它由 `ods_pay_refund_di` 关联 `ods_trade_order_di` 加工，补齐了收货省份。想做退款金额、退款原因、分省份退款的灵活分析，从它入手，比如 `SELECT refund_reason, SUM(refund_amount) FROM dwd_trade_refund_di GROUP BY refund_reason`。

## 字段

- `refund_id` 是 bigint 的退款单ID，是本表主键。
- `order_id` 是 bigint 的订单ID，关联对应订单。
- `buyer_id` 是 bigint 的买家用户ID，按买家看退款用它。
- `refund_amount` 是 decimal(12,2) 的退款金额，单位元，可按日期或维度相加，例如 `SUM(refund_amount)`。
- `refund_reason` 是 varchar(20) 的退款原因，是分析退款结构的主要维度。
- `receiver_province` 是 varchar(20) 的收货省份，从订单带入。
- `refund_date` 是 date 的退款完成日期，退款按此日期归属。
- `dt` 是 date 的分区日期，用于分区裁剪，别当退款时间用。

## 上下游

`dwd_trade_refund_di` 的上游是 `ods_pay_refund_di` 和 `ods_trade_order_di`。它的下游是省份、原因、买家粒度的日/周/月汇总表，如 `dws_trade_prov_refund_1d`、`dws_trade_reason_refund_1m`、`dws_trade_user_refund_1w` 等，都从这张明细聚合而来。

## 使用注意

`dwd_trade_refund_di` 适合做退款金额和退款单量的灵活下钻，比如按省份 `SELECT receiver_province, SUM(refund_amount) FROM dwd_trade_refund_di GROUP BY receiver_province`，或按 `refund_reason` 看原因分布。固定口径的汇总建议用对应 DWS 表。注意退款按 `refund_date`（完成日期）归属，与按下单月的退款率口径不同；区分 `refund_date` 与 `dt`。本家族有备份表 `dwd_trade_refund_di_bak`、带日期备份 `dwd_trade_refund_di_bak_20250226` 和测试表 `test_dwd_trade_refund_di`，均为时点副本或调试产物，不能用于统计，口径一律用 `dwd_trade_refund_di`。
