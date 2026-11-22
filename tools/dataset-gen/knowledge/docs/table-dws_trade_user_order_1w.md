# 表说明：dws_trade_user_order_1w（DWS-买家用户粒度下单汇总（周））

## 概述

`dws_trade_user_order_1w` 按统计周和买家用户汇总下单。

- 当前 15,568 行。
- `stat_date` 存周一日期，一周一行。
- 来源 `dwd_trade_order_detail_di`，口径与日表月表一致。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配。
- `buyer_id`：买家用户ID，`bigint`，和 `stat_date` 共同唯一，是用户维度主键。
- `order_cnt`：下单数，当周去重后的订单计数，可跨周相加。
- `item_qty`：购买件数，含未支付订单，口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分。

## 上下游

- 来源：交易明细表 `dwd_trade_order_detail_di`。
- 加工：按周一日期和 `buyer_id` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：用户级周下单、周活买家数（`COUNT(DISTINCT buyer_id)`）、人均周下单等。
- 注意：`buyer_id` 跨周不可直接相加去重，区间活跃用户要重新去重。
- 不适合：日粒度用日表，跨月对比用月表。
- 同家族噪声表：带日期备份 `dws_trade_user_order_1w_bak_20251211`、测试表 `test_dws_trade_user_order_1w`。
- 备份是历史快照、测试表是调试产物，数据可能过期或残缺，统计一律用本表。
