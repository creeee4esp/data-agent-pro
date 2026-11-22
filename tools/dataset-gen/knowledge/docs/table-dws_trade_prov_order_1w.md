# 表说明：dws_trade_prov_order_1w（DWS-收货省份粒度下单汇总（周））

## 概述

`dws_trade_prov_order_1w` 为收货省份维度的下单周汇总表，当前共 848 行。该表以统计周与收货省份为粒度对下单行为按周聚合，`stat_date` 存储每周周一日期以标识所属周。数据源自交易明细表 `dwd_trade_order_detail_di`，下单数按订单去重计数，购买件数与已支付金额求和，口径与同家族日表、月表一致。

## 字段

- `stat_date`：统计周，取值为周一日期，按周筛选须以该周周一日期匹配，按区间筛选使用 `BETWEEN`。
- `receiver_province`：收货省份，与 `stat_date` 共同构成行粒度，同一周同一省份仅一行。
- `order_cnt`：下单数，为去重后的订单计数，可按周累加求区间总量。
- `item_qty`：购买件数，包含未支付订单，统计销量时须注意该口径偏大。
- `paid_amount`：已支付订单的明细金额（元），仅累计已支付部分，不应等同于全部下单金额。

## 上下游

数据来源为交易明细表 `dwd_trade_order_detail_di`，加工时按周一日期与 `receiver_province` 分组聚合写入本表。当前无登记在册的下游表，如需更高层级汇总，通常直接基于本表再行聚合。

## 使用注意

该表适用于省份维度的周趋势分析、周区间汇总及省份间对比。若需日粒度数据应改用日表，跨月对比建议使用月表。该家族含多类噪声表：历史归档 `dws_trade_prov_order_1w_his`、备份 `dws_trade_prov_order_1w_bak`、带日期备份 2 张（如 `dws_trade_prov_order_1w_bak_20250308`、`dws_trade_prov_order_1w_bak_20250801`）及测试表 `test_dws_trade_prov_order_1w`。此类表为归档、快照或调试产物，数据可能不完整或已过期，不得用于统计，一切以本表为准。
