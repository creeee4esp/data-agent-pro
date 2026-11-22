# 表说明：dws_trade_user_order_1d（DWS-用户粒度交易汇总（日））

## 概述

`dws_trade_user_order_1d` 按统计日期和买家用户汇总每日交易。

- 当前 19,102 行。
- 来源 `dwd_trade_order_detail_di`。
- 下单数去重、支付订单数去重、支付金额求和。
- 是本家族里唯一有登记下游的表。

## 字段

- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`。
- `buyer_id`：买家用户ID，`bigint`，和 `stat_date` 共同唯一，是用户维度的主键。
- `order_cnt`：下单数，按订单去重计数，可跨天相加。
- `paid_order_cnt`：支付订单数，已支付订单去重计数，与 `order_cnt` 口径不同，别混用。
- `paid_amount`：支付订单明细金额，`decimal(14,2)`，只累计已支付部分。

## 上下游

- 来源：交易明细表 `dwd_trade_order_detail_di`。
- 加工：按 `order_date` 和 `buyer_id` 分组聚合写入。
- 下游：`ads_trade_gmv_daily` 使用本表。

## 使用注意

- 适合：用户级每日交易、活跃买家数（按 `buyer_id` 去重）、人均下单等分析。
- 注意：`buyer_id` 跨天不可直接相加去重，统计区间活跃用户要用 `COUNT(DISTINCT buyer_id)`。
- 同家族噪声表：备份 `dws_trade_user_order_1d_bak`、8 张带日期备份（如 `dws_trade_user_order_1d_bak_20250203`、`dws_trade_user_order_1d_bak_20250219`）。
- 备份是历史快照，数据可能过期，统计一律用本表。
