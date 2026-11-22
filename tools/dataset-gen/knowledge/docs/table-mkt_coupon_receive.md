# 表说明：mkt_coupon_receive（用户领券记录表）

## 概述

`mkt_coupon_receive` 是 CORE 层的用户领券记录表，记录每一次领券及其后续核销。

- 粒度：一条领券记录一行。
- 当前规模：6,739 行。
- 适用场景：领券量、核销率、券与用户维度的营销分析。

## 字段

- `receive_id`（bigint）：领券记录 ID，单条领券的唯一标识。
- `coupon_id`（int）：优惠券 ID，指向 `mkt_coupon.coupon_id`，带出券规则用它。
- `user_id`（bigint）：领券用户 ID，按用户聚合领券用它。
- `receive_time`（datetime）：领取时间，按领券日期统计用它。
- `use_status`（varchar(10)）：使用状态，used-已使用、unused-未使用，算核销率用它过滤。
- `used_order_id`（bigint）：使用该券的订单 ID，未使用为空，统计核销订单前要先排除空值。
- `use_time`（datetime）：使用时间，未使用时无值，算领取到核销的时间差要注意空值。

## 上下游

`mkt_coupon_receive` 的上游是业务系统（在线数据库）。

- 下游：ODS 层的 `ods_mkt_coupon_receive_di`，由它承接再往上层加工。
- 关联：`mkt_coupon_receive.coupon_id` 指向 `mkt_coupon.coupon_id`，据此带出券的类型、门槛和面额。

## 使用注意

`mkt_coupon_receive` 适合做领券量、核销率、单券或单用户的领用核销分析。

- 不适合：查询券本身的规则定义，那在 `mkt_coupon` 里。
- 噪声表：备份表 `mkt_coupon_receive_bak`、带日期备份 `mkt_coupon_receive_bak_20250810`。
- 为什么不能用：它们是历史快照，口径与时效都不保证。
- 正式取数只认 `mkt_coupon_receive`。
