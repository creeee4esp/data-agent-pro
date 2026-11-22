# 表说明：dws_trade_prov_refund_1w（DWS-收货省份粒度退款汇总（周））

## 概述

`dws_trade_prov_refund_1w` 按周、按收货省份汇总退款。

- 当前 536 行。
- `stat_date` 存周一日期，一周一行。
- 来源 `dwd_trade_refund_di`，按退款完成日期归属。
- `refund_cnt` 退款记录条数，`refund_amount` 退款金额求和。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配。
- `receiver_province`：收货省份，和 `stat_date` 共同唯一，同周同省一行。
- `refund_cnt`：退款笔数，当周该省份退款记录条数，可跨周相加。
- `refund_amount`：退款金额（元），当周该省份退款金额之和，可跨周相加。

## 上下游

- 来源：退款明细表 `dwd_trade_refund_di`。
- 加工：按周一日期和 `receiver_province` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：省份的周退款走势、周区间汇总、省份间对比。
- 注意：按退款完成日期归属。
- 不适合：日粒度用日表，跨月对比用月表。
- 同家族噪声表：3 张带日期备份（如 `dws_trade_prov_refund_1w_bak_20250129`、`dws_trade_prov_refund_1w_bak_20250216`）、旧版本 `dws_trade_prov_refund_1w_old`。
- 这些是快照或改版遗留，数据可能过期，统计一律用本表。
