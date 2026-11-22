# 表说明：ads_trade_refund_rate_monthly（ADS-月度退款率）

## 概述

`ads_trade_refund_rate_monthly` 是按下单月统计的全站月度退款率，位于 ADS 层，表注释为 ADS-月度退款率，当前 12 行，一年每月一行。它由订单表 `ods_trade_order_di` 加工，只统计已支付的订单。想回答「某月退款率多少」，一句 `SELECT stat_month, refund_rate FROM ads_trade_refund_rate_monthly WHERE stat_month = '2025-06'` 就能拿到。

## 字段

- `stat_month` 是 char(7) 的统计月份，按下单月归属，例如退款率看的是该月下单订单后续的退款情况。
- `paid_order_cnt` 是 int 的支付订单数，是退款率的分母，表示当月下单且已支付的订单数。
- `refund_order_cnt` 是 int 的退款订单数，是分子，表示这批订单中状态变为退款的订单数。
- `refund_rate` 是 decimal(8,4) 的退款率，等于 `refund_order_cnt` 除以 `paid_order_cnt`，已算好可直接取用，想自己核算时如 `SELECT refund_order_cnt / paid_order_cnt FROM ads_trade_refund_rate_monthly` 即可验证。

## 上下游

`ads_trade_refund_rate_monthly` 的唯一上游是 `ods_trade_order_di`，加工时只取 pay_time 不为空的订单，按下单月分组统计支付订单数、退款订单数并相除。它没有下游表，是面向报表的末端结果。

## 使用注意

`ads_trade_refund_rate_monthly` 适合看月度整体退款率走势，比如 `SELECT stat_month, refund_rate FROM ads_trade_refund_rate_monthly ORDER BY stat_month`。要算区间整体退款率，别把多行 `refund_rate` 平均，应分别汇总 `refund_order_cnt` 与 `paid_order_cnt` 再相除。它不支持按品类、省份、原因拆分退款。本家族有备份表 `ads_trade_refund_rate_monthly_bak`、带日期备份 `ads_trade_refund_rate_monthly_bak_20250810` 和旧版本表 `ads_trade_refund_rate_monthly_old`，它们分别是时点副本和历史口径残留，都不保证完整准确，统计一律用 `ads_trade_refund_rate_monthly`。
