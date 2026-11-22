# 表说明：ods_pay_refund_di（ODS-退款单表）

## 概述

`ods_pay_refund_di` 是 ODS 层的退款单贴源表，原样承接业务侧的退款记录，当前 1,031 行，一行一笔退款。相比业务表，它多了 `etl_time` 和 `dt` 两个技术字段。它是退款金额、退款原因分析往上加工的起点，和支付、订单链路配合起来才能算出退款率这类指标。

## 字段

- `refund_id`（bigint）：退款单 ID，单笔退款的唯一标识。
- `order_id`（bigint）：订单 ID，想把退款挂回订单就靠它关联。
- `refund_amount`（decimal(12,2)）：退款金额，单位元，不超过订单实付金额，汇总退款额以它为准。
- `refund_reason`（varchar(20)）：退款原因，quality-质量问题、wrong_item-发错货、no_longer_needed-不想要了、late_delivery-物流太慢，做原因分布用这些枚举值。
- `apply_time`（datetime）：申请时间，按退款申请日期统计用它。
- `finish_time`（datetime）：退款完成时间，算退款处理时长用它和申请时间相减。
- `etl_time`（datetime）：ETL 同步时间，技术字段，不是业务时间，别拿它做业务口径。
- `dt`（date）：分区日期，查询时用它裁剪分区。

## 上下游

`ods_pay_refund_di` 的上游是 `pay_refund`，通过 ETL 同步落到 ODS 层。它的下游是 `dwd_trade_refund_di`，由后者在明细层承接退款数据继续加工。

## 使用注意

`ods_pay_refund_di` 适合做退款金额汇总、退款原因分布和退款处理时长分析，查询时建议带 `dt` 做分区过滤。它只记退款本身，算退款率要结合订单或支付数据一起看，别单用这张表就下结论。库里同家族还有噪声表：备份表 `ods_pay_refund_di_bak`、带日期备份如 `ods_pay_refund_di_bak_20250308`、`ods_pay_refund_di_bak_20251216`。它们是某次操作前的历史快照，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_pay_refund_di`。
