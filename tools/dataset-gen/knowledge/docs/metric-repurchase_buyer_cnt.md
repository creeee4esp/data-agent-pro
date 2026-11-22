# 指标说明：支付订单数≥2的买家数（repurchase_buyer_cnt）

## 计算公式

`ads_user_repurchase_yearly` 里的复购买家数统计的是一年内支付订单数达到 2 笔及以上的买家数量。

- 口径：先按 `YEAR(order_time)` 和 `buyer_id` 统计每个买家当年的支付订单数 `cnt`，再 `SUM(cnt >= 2)` 得到复购买家数。
- 统计范围：限定 `pay_time IS NOT NULL`，只看支付过的订单。
- 时间归属：按自然年 `YEAR(order_time)` 归属。
- 可加和：否。去重买家数不能跨年相加。

## 取哪张表

查复购买家数只有一张表。

- 年度口径：`ads_user_repurchase_yearly`，按自然年给出。

## 来源与历史变化

复购买家数的报表加工回溯到订单表 `ods_trade_order_di`。

- 加工 SQL：`INSERT INTO ads_user_repurchase_yearly SELECT y, COUNT(*), SUM(cnt >= 2), ROUND(SUM(cnt >= 2) / COUNT(*), 4) FROM (SELECT YEAR(order_time) AS y, buyer_id, COUNT(*) AS cnt FROM ods_trade_order_di WHERE pay_time IS NOT NULL GROUP BY YEAR(order_time), buyer_id) t GROUP BY y`。
- 历史变更：自上线以来未调整计算规则。
