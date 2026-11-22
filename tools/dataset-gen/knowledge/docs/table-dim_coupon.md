# 表说明：dim_coupon（DIM-优惠券维表）

## 概述

`dim_coupon` 是优惠券的维表，属于 DIM 层，表注释为 DIM-优惠券维表，当前 30 行，一券一行。它由 `ods_mkt_coupon_di` 加工，提供券的名称、类型、门槛和面额，主要用来给领用/核销的事实表补充券的属性。典型用法是关联后按类型分析，如 `SELECT coupon_type, COUNT(*) FROM dim_coupon GROUP BY coupon_type`。

## 字段

- `coupon_id` 是 int 的优惠券ID，是本表主键，也是关联事实表的键。
- `coupon_name` 是 varchar(50) 的优惠券名称，用于展示，不建议用名称做关联。
- `coupon_type` 是 varchar(20) 的类型，是分析券结构最常用的维度。
- `threshold_amt` 是 decimal(10,2) 的门槛金额，单位元，表示满多少可用，0 可理解为无门槛。
- `face_value` 是 decimal(10,2) 的面额，单位元，表示券的抵扣金额。

## 上下游

`dim_coupon` 的唯一上游是 `ods_mkt_coupon_di`，加工时直接取券的 ID、名称、类型、门槛和面额。它的下游是 `ads_coupon_usage_monthly`，在月度核销统计中由 `dim_coupon` 提供券类型。例如领券明细关联本表即可拿到 `coupon_type` 再按类型聚合。

## 使用注意

`dim_coupon` 适合做券属性的查询和关联，比如查某券的面额门槛、按 `coupon_type` 统计券的数量和结构。它是维表，不含领取、使用等事实数据，不能用它直接统计领取数或核销率，那要去 `ads_coupon_usage_monthly` 或领券明细。本家族有备份表 `dim_coupon_bak` 和带日期备份 `dim_coupon_bak_20250204`，二者是某时点的留存副本，不随任务更新、可能缺列或过期，关联和查询一律用 `dim_coupon`，不要用备份。
