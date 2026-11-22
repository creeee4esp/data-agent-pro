# 表说明：ads_user_repurchase_yearly（ADS-年度复购率）

## 概述

`ads_user_repurchase_yearly` 是按自然年统计的复购率，位于 ADS 层，表注释为 ADS-年度复购率，当前只有 1 行，对应目前覆盖的一个统计年份。它由订单表 `ods_trade_order_di` 加工，只看已支付订单。取数很简单，`SELECT stat_year, repurchase_rate FROM ads_user_repurchase_yearly` 就能拿到当年的复购率。

## 字段

- `stat_year` 是 smallint 的统计年份，是本表的粒度，一年一行。
- `buyer_cnt` 是 int 的支付买家数，是复购率的分母，表示当年有过支付订单的去重买家数。
- `repurchase_buyer_cnt` 是 int 的复购买家数，指当年支付订单数大于等于 2 的买家数，是分子。
- `repurchase_rate` 是 decimal(8,4) 的复购率，等于 `repurchase_buyer_cnt` 除以 `buyer_cnt`，已算好直接用，想核对时 `SELECT repurchase_buyer_cnt / buyer_cnt FROM ads_user_repurchase_yearly` 即可。

## 上下游

`ads_user_repurchase_yearly` 的唯一上游是 `ods_trade_order_di`，加工时先取 pay_time 不为空的订单，按年和买家统计下单次数，再按年汇总出买家数、支付订单数≥2 的买家数和复购率。它没有下游表，是面向报表的末端结果。

## 使用注意

`ads_user_repurchase_yearly` 适合回答「某年复购率是多少、有多少复购买家」这类年度问题，直接读一行即可。它不支持按月、季度或任何维度拆分复购，也不能据此推算月度复购。复购的定义固定为当年支付订单数≥2，换口径要重算明细。本家族有备份表 `ads_user_repurchase_yearly_bak`、带日期备份 `ads_user_repurchase_yearly_bak_20251004` 和测试表 `test_ads_user_repurchase_yearly`，都是时点副本或调试产物，不能用于统计，口径一律认 `ads_user_repurchase_yearly`。
