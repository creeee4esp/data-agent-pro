# 表说明：ods_mkt_coupon_receive_di（ODS-用户领券记录表）

## 概述

`ods_mkt_coupon_receive_di` 是 ODS 层的用户领券记录贴源表，原样承接领券及核销明细，当前 6,739 行，一行一次领券。相比业务表多了 `etl_time` 和 `dt` 两个技术字段。它是领券量、核销率等营销指标往上加工的起点。这里先点一个容易踩的坑：算核销只能看 `use_status = 'used'`，别拿 `used_order_id` 非空当唯一判断，两者语义要对齐使用。

## 字段

- `receive_id`（bigint）：领券记录 ID，单条领券的唯一标识。
- `coupon_id`（int）：优惠券 ID，指向 `mkt_coupon.coupon_id`，带出券规则用它。
- `user_id`（bigint）：领券用户 ID，按用户聚合领券用它。
- `receive_time`（datetime）：领取时间，按领券日期统计用它。
- `use_status`（varchar(10)）：使用状态，used-已使用、unused-未使用，算核销率以它为准。
- `used_order_id`（bigint）：使用该券的订单 ID，未使用为空，关联核销订单前先排除空值。
- `use_time`（datetime）：使用时间，未使用时无值，算领取到核销时间差要注意空值。
- `etl_time`（datetime）：ETL 同步时间，技术字段，不是业务时间，别用来做领券或核销的时间口径。
- `dt`（date）：分区日期，查询时用它裁剪分区。

## 上下游

`ods_mkt_coupon_receive_di` 的上游是 `mkt_coupon_receive`，通过 ETL 同步落到 ODS 层。它的下游是 `ads_coupon_usage_monthly`，由后者承接做月度核销相关的汇总。

## 使用注意

`ods_mkt_coupon_receive_di` 适合做领券量、核销率、单券或单用户的领用核销分析，查询时建议带 `dt` 做分区过滤。它不含券的规则定义，查门槛、面额要去券定义相关表。库里同家族还有噪声表：备份表 `ods_mkt_coupon_receive_di_bak`、带日期备份 `ods_mkt_coupon_receive_di_bak_20251010`。它们是历史快照，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_mkt_coupon_receive_di`。
