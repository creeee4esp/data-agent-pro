# 加工说明：ads_trade_gmv_daily

## 加工逻辑

`ads_trade_gmv_daily` 存的是 ADS-每日GMV报表，按天汇总，当前 365 行，正好覆盖一年每天一行。它的上游是 DWS 层的 `dws_trade_user_order_1d`，已经按"用户 + 天"聚过一层，这里再按 `stat_date` 汇总到天。

每个 `stat_date` 下算三件事：支付金额用 `SUM(paid_amount)`，支付订单数用 `SUM(paid_order_cnt)`，下单用户数用 `SUM(IF(paid_order_cnt > 0, 1, 0))`，即当天有支付订单（`paid_order_cnt > 0`）的用户记 1 再累加。

```sql
SELECT stat_date, SUM(paid_amount), SUM(paid_order_cnt), SUM(IF(paid_order_cnt > 0, 1, 0))
FROM dws_trade_user_order_1d
GROUP BY stat_date
```

举个取数例子：要看"某一天的 GMV 和下单用户数"，按 `stat_date` 过滤那天即可，不用回 `dws_trade_user_order_1d` 自己按用户聚合。再比如看全年 GMV 趋势，直接读这 365 行画折线就行。

## 依赖与调度

`ads_trade_gmv_daily` 依赖一张上游表，DWS 层的 `dws_trade_user_order_1d`。调度每日 05:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`ads_trade_gmv_daily` 发布前跑四项检查：主键 / 粒度唯一，保证 `stat_date` 一天只有一行；关键字段非空；行数与前一天相比波动超过 30% 告警；可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

检查失败时下游会直接受影响。`stat_date` 粒度重复会让某天 GMV 被算两遍，监控大盘出现假尖峰；行数骤降往往是 `dws_trade_user_order_1d` 当天没产出全，用它看趋势会出现断点。看到告警先定位上游再发布。
