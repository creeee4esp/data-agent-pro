# 表说明：dws_trade_prov_pay_1w（DWS-收货省份粒度支付汇总（周））

## 概述

`dws_trade_prov_pay_1w` 按周、按收货省份汇总支付。

- 当前 848 行。
- `stat_date` 存周一日期，一周一行。
- 来源 `dwd_trade_pay_di`，按支付日期归属。
- `pay_cnt` 支付记录条数，`pay_amt` 金额求和。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配。
- `receiver_province`：收货省份，和 `stat_date` 共同唯一，同周同省一行。
- `pay_cnt`：支付笔数，当周该省份支付记录条数，可跨周相加。
- `pay_amt`：支付金额（元），当周该省份金额之和，可跨周相加。

## 上下游

- 来源：支付明细表 `dwd_trade_pay_di`。
- 加工：按周一日期和 `receiver_province` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：省份的周支付走势、周区间汇总、省份间对比。
- 注意：按支付日期归属，与按下单日期归属的口径不同。
- 不适合：日粒度用日表，跨月对比用月表。
- 同家族噪声表：备份 `dws_trade_prov_pay_1w_bak`、6 张带日期备份（如 `dws_trade_prov_pay_1w_bak_20250116`、`dws_trade_prov_pay_1w_bak_20250305`）、旧版本 `dws_trade_prov_pay_1w_v2`。
- 这些是快照或改版遗留，数据可能过期或口径不同，统计一律用本表。
