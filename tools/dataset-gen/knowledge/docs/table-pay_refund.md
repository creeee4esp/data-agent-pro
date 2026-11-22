# 表说明：pay_refund（退款单表）

## 概述

`pay_refund` 为 CORE 层的退款单表，记录订单的退款申请与完成情况，当前共 1,031 行。该表以单笔退款为粒度，一行对应一次退款，可用于退款金额、退款原因分布及退款处理时长等分析。作为交易退款链路的贴源来源，其数据经由业务系统同步而来。

## 字段

- `refund_id`（bigint）：退款单 ID，单笔退款的唯一标识。
- `order_id`（bigint）：订单 ID，用于关联订单，将退款挂回订单时以该字段为准。
- `refund_amount`（decimal(12,2)）：退款金额，单位元，不超过订单实付金额，汇总退款额以该字段为准。
- `refund_reason`（varchar(20)）：退款原因，取值为 quality-质量问题、wrong_item-发错货、no_longer_needed-不想要了、late_delivery-物流太慢，按原因分布应使用对应枚举值。
- `apply_time`（datetime）：申请时间，按退款申请日期统计时以该字段为准。
- `finish_time`（datetime）：退款完成时间，计算退款处理时长时以其与申请时间相减。

## 上下游

`pay_refund` 的数据来自业务系统（在线数据库），同步后进入数据平台。其下游为 ODS 层的 `ods_pay_refund_di`，由后者承接并向上层加工。

## 使用注意

`pay_refund` 适合用于退款金额汇总、退款原因分布及退款处理时长分析，不适合单独用于退款率统计，该指标须结合订单或支付数据一并计算。库内存在若干同家族噪声表：备份表 `pay_refund_bak`、带日期备份 `pay_refund_bak_20250212`、`pay_refund_bak_20250812` 以及按月拆分的月分表（范围为 `pay_refund_202301 ～ pay_refund_202512`，如 `pay_refund_202301`）、测试表 `test_pay_refund`。上述表分别为历史快照、按期拆分存档或调试用途，其口径与完整性不受保证，不得用于正式统计。正式取数一律以 `pay_refund` 为准。
