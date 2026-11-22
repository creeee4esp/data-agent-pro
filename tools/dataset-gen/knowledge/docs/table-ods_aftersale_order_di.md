# 表说明：ods_aftersale_order_di（ODS-售后单表）

## 概述

`ods_aftersale_order_di` 是 ODS 层的售后单表，原样承接业务侧的售后申请记录，当前 1,025 行。它以单条售后单为粒度，一行一次售后申请，是退货、换货、维修分析的起点。作为 ODS 表，它比业务表多了 `etl_time` 和 `dt` 两个技术字段，用来标记同步时间和分区，平时做统计别把这两个当业务量看。

## 字段

- `aftersale_id`（bigint）：售后单 ID，单条售后的唯一标识。
- `order_item_id`（bigint）：订单明细 ID，即 `trade_order_item.id`，想追到是哪笔明细申请的售后靠它关联。
- `aftersale_type`（varchar(10)）：售后类型，return-退货、exchange-换货、repair-维修，按类型拆分用这些枚举值。
- `apply_time`（datetime）：申请时间，晚于签收时间，按申请日期统计时要留意这个时序。
- `status`（varchar(10)）：处理状态，processing-处理中、closed-已完结、rejected-已驳回，算完结率或驳回率用它过滤。
- `etl_time`（datetime）：ETL 同步时间，是技术字段，不代表业务发生时间，别用来做业务口径的时间统计。
- `dt`（date）：分区日期，用于按分区裁剪扫描范围，查某个分区时用它过滤。

## 上下游

`ods_aftersale_order_di` 的数据来自上游的 `aftersale_order`，通过 ETL 同步落到 ODS 层。目前它没有登记下游表，属于链路末端的贴源数据。

## 使用注意

`ods_aftersale_order_di` 适合做售后类型分布、处理状态分析和按明细回溯售后，查询时建议带上 `dt` 做分区过滤以减少扫描。它不适合承载退款金额，退款金额在支付退款链路里。库里同家族还有噪声表：备份表 `ods_aftersale_order_di_bak`、带日期备份如 `ods_aftersale_order_di_bak_20250729`、测试表 `test_ods_aftersale_order_di`。它们是历史快照或调试数据，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_aftersale_order_di`。
