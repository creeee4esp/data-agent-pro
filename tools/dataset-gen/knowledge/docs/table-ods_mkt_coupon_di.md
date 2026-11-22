# 表说明：ods_mkt_coupon_di（ODS-优惠券定义表）

## 概述

`ods_mkt_coupon_di` 是 ODS 层的优惠券定义贴源表，原样承接业务侧的券规则，当前 30 行，一行一种券。相比业务表，它多了 `etl_time` 和 `dt` 两个技术字段。它是营销链路上券维度数据的起点。举个例子，想看各类型券各有多少种，`SELECT coupon_type, COUNT(*) FROM ods_mkt_coupon_di WHERE dt = '某分区' GROUP BY coupon_type` 就能拿到。

## 字段

- `coupon_id`（int）：优惠券 ID，券的唯一标识，下游带出券规则靠它。
- `coupon_name`（varchar(50)）：优惠券名称，展示用，统计别拿名称当键。
- `coupon_type`（varchar(20)）：类型，full_reduction-满减、discount-折扣、no_threshold-无门槛，按类型拆分用这些值。
- `threshold_amt`（decimal(10,2)）：使用门槛金额，单位元，无门槛为 0，筛高门槛券对它比大小。
- `face_value`（decimal(10,2)）：面额，单位元，折扣券这里是最高抵扣金额，算力度要区分券类型。
- `valid_from`（date）：有效期开始，判断某日可用的左边界。
- `valid_to`（date）：有效期结束，判断某日可用的右边界，例如取某日有效券用 `valid_from <= '某日' AND valid_to >= '某日'`。
- `etl_time`（datetime）：ETL 同步时间，技术字段，不是业务时间。
- `dt`（date）：分区日期，查询时用它裁剪分区。

## 上下游

`ods_mkt_coupon_di` 的上游是 `mkt_coupon`，通过 ETL 同步落到 ODS 层。它的下游是 `dim_coupon`，由后者承接做券维度加工。

## 使用注意

`ods_mkt_coupon_di` 适合做券规则查询和按类型、门槛的券归类，查询时建议带 `dt` 过滤，例如统计各 `coupon_type` 的券数量或筛某时段有效券。它只描述券定义，不含领用核销，领券量、核销率要去领券记录相关表取。库里同家族还有噪声表：历史归档 `ods_mkt_coupon_di_his`、带日期备份如 `ods_mkt_coupon_di_bak_20250107`、测试表 `test_ods_mkt_coupon_di`。它们是归档、历史快照或调试数据，口径和时效都不保证，不能拿来跑统计，正式取数只认 `ods_mkt_coupon_di`。
