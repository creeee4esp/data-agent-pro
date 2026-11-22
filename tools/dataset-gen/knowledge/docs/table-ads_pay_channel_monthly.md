# 表说明：ads_pay_channel_monthly（ADS-支付渠道月度报表）

## 概述

`ads_pay_channel_monthly` 按支付渠道汇总每月支付情况，属 ADS 层，表注释为 ADS-支付渠道月度报表，当前 49 行。粒度为月份 × 支付渠道。上游为 `dwd_trade_pay_di`。常用于看各渠道月度支付笔数和金额占比。

## 字段

- `stat_month`：char(7)，统计月份，按支付日期归属。
- `pay_channel`：varchar(20)，支付渠道，本表除月份外的唯一拆分维度。
- `pay_cnt`：int，支付笔数，按渠道按月累计。
- `pay_amt`：decimal(16,2)，支付金额，单位元，可跨渠道相加得总额。

## 上下游

`ads_pay_channel_monthly` 数据来自 `dwd_trade_pay_di`（支付明细），按支付日期的月份和支付渠道分组，汇总笔数与金额。无下游表，为末端结果表。

## 使用注意

- 适合：看某月各渠道支付笔数、支付金额及渠道占比，直接取数。
- 不适合：下钻到省份、买家、订单；按下单口径统计，支付是按支付日期归属，与 GMV 的下单口径不一致。
- 噪声表：备份表 `ads_pay_channel_monthly_bak`、测试表 `test_ads_pay_channel_monthly`。备份是时点副本、测试表是调试产物，均不保证完整或最新，不能用于统计。
- 口径：统计一律用 `ads_pay_channel_monthly`。
