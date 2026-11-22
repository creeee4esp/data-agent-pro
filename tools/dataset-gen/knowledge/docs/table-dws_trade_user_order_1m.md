# 表说明：dws_trade_user_order_1m（DWS-买家用户粒度下单汇总（月））

## 概述

`dws_trade_user_order_1m` 按统计月和买家用户汇总下单。

- 当前 9,300 行。
- `stat_date` 存月初日期，代表整月。
- 来源 `dwd_trade_order_detail_di`，口径与日表周表一致。

## 字段

- `stat_date`：统计月，取月初日期，按月筛选用月初日期匹配。
- `buyer_id`：买家用户ID，`bigint`，和 `stat_date` 共同唯一，是用户维度主键。
- `order_cnt`：下单数，当月去重后的订单计数，可跨月相加。
- `item_qty`：购买件数，含未支付订单，口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分。

## 上下游

- 来源：交易明细表 `dwd_trade_order_detail_di`。
- 加工：按月初日期和 `buyer_id` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：用户级月度下单、月活买家数（`COUNT(DISTINCT buyer_id)`）、人均月下单等。
- 注意：`buyer_id` 跨月不可直接相加去重，区间活跃用户要重新去重。
- 同家族噪声表：备份 `dws_trade_user_order_1m_bak`、6 张带日期备份（如 `dws_trade_user_order_1m_bak_20250116`、`dws_trade_user_order_1m_bak_20250327`）、旧版本 `dws_trade_user_order_1m_v2`。
- 这些是快照或改版遗留，数据可能过期或口径不同，统计一律用本表。
