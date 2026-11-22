# 表说明：ads_coupon_usage_monthly（ADS-优惠券月度使用情况）

## 概述

`ads_coupon_usage_monthly` 是优惠券领用与核销的月度汇总，位于 ADS 层，表注释为 ADS-优惠券月度使用情况，当前共 39 行。它按「领取月」和优惠券类型两个维度聚合，由 `ods_mkt_coupon_receive_di` 关联 `dim_coupon` 加工得到。这里最容易踩的一点是口径：`stat_month` 是按券的领取时间划分的，而不是按使用时间，所以同一个月的核销率分母和分子都来自当月领取的券。

## 字段

- `stat_month` 是 char(7) 的统计月份，按领取月归属，形如 2025-06，跨月分析时要记住它锁定的是领券动作而非用券动作。
- `coupon_type` 是 varchar(20) 的优惠券类型，与 `dim_coupon` 中的类型一致，是本表除月份外唯一的拆分维度。
- `receive_cnt` 是 int 的领取数，表示当月该类型券被领取的张数。
- `used_cnt` 是 int 的使用数，表示这批领取的券中被核销的张数，注意它统计的是当月领取且已核销的券。
- `use_rate` 是 decimal(8,4) 的核销率，等于 `used_cnt` 除以 `receive_cnt`，已经预先算好，直接取用即可，不要再用多行的 `used_cnt` 与 `receive_cnt` 简单相除求整体核销率，否则会算错。

## 上下游

`ads_coupon_usage_monthly` 的数据来自 `ods_mkt_coupon_receive_di`（领券明细）与 `dim_coupon`（优惠券维表），前者提供领取与核销状态，后者补充券类型。加工时按领取月和券类型分组统计。它目前没有下游表，属于直接面向报表和分析的末端结果表。

## 使用注意

`ads_coupon_usage_monthly` 适合回答「某月各类型券领了多少、核销率如何」这类月度问题，直接读取即可。它不适合做按使用时间的核销分析，因为口径锁定在领取月；也不适合跨月把多行 `use_rate` 直接平均，若要整体核销率应分别汇总领取数与使用数再相除。此外存在历史归档表 `ads_coupon_usage_monthly_his`，它是旧数据的留存快照，不随当前任务更新，口径和时间范围都可能与正表不同，统计时一律用 `ads_coupon_usage_monthly`，不要用归档表。
