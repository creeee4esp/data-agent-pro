# 表说明：dws_trade_prov_pay_1d（DWS-收货省份粒度支付汇总（日））

## 概述

`dws_trade_prov_pay_1d` 按统计日期和收货省份汇总每日支付。

- 当前 5,149 行。
- 来源 `dwd_trade_pay_di`，按支付日期归属。
- `pay_cnt` 是支付记录条数，`pay_amt` 是金额求和。

## 字段

- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`。
- `receiver_province`：收货省份，和 `stat_date` 共同唯一，同日同省一行。
- `pay_cnt`：支付笔数，当日该省份的支付记录条数，可跨天相加。
- `pay_amt`：支付金额（元），当日该省份金额之和，可跨天相加。

## 上下游

- 来源：支付明细表 `dwd_trade_pay_di`。
- 加工：按 `pay_date` 和 `receiver_province` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：省份的每日支付笔数金额走势、日区间汇总、省份间对比。
- 注意：按支付日期归属，与按下单日期归属的下单类口径不同。
- 不适合：日以下或日以上的其他粒度，请用对应周表、月表。
- 同家族噪声表：备份 `dws_trade_prov_pay_1d_bak`、3 张带日期备份（如 `dws_trade_prov_pay_1d_bak_20250127`、`dws_trade_prov_pay_1d_bak_20250214`）、旧版本 `dws_trade_prov_pay_1d_old`、测试表 `test_dws_trade_prov_pay_1d`。
- 这些是快照、改版遗留或调试产物，数据可能过期或残缺，统计一律用本表。
