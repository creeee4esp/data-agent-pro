# 指标说明：复购率（repurchase_rate）

## 计算公式

`ads_user_repurchase_yearly` 里的复购率是一个比率，口径为 `ROUND(SUM(cnt >= 2) / COUNT(*), 4)`。理解起来是这样：先按 `YEAR(order_time)` 和 `buyer_id` 算出每个买家当年支付过几笔订单 `cnt`，分母 `COUNT(*)` 是当年有支付的买家总数，分子 `SUM(cnt >= 2)` 是其中支付订单数达到 2 笔及以上的买家数，两者相除保留四位小数。统计只看支付过的订单（`pay_time IS NOT NULL`），按自然年归属。要提醒新同事的是，比率不可加和，不同年的复购率不能平均，想看多年整体复购得把分子分母各自汇总后再除。

## 取哪张表

查复购率就用年度复购报表 `ads_user_repurchase_yearly`，按自然年取对应那一行即可。这张表已经把复购买家数、买家总数和比率都算好，无需自己从订单表临时拼。

## 来源与历史变化

复购率的报表加工回溯到订单表 `ods_trade_order_di`，加工 SQL 为 `INSERT INTO ads_user_repurchase_yearly SELECT y, COUNT(*), SUM(cnt >= 2), ROUND(SUM(cnt >= 2) / COUNT(*), 4) FROM (SELECT YEAR(order_time) AS y, buyer_id, COUNT(*) AS cnt FROM ods_trade_order_di WHERE pay_time IS NOT NULL GROUP BY YEAR(order_time), buyer_id) t GROUP BY y`。历史变更方面，2025-02-09 由数据平台组发布说明：复购率按自然年统计，支付订单数≥2 视为复购。
