# 加工说明：ads_user_repurchase_yearly

## 加工逻辑

`ads_user_repurchase_yearly` 存的是 ADS-年度复购率，按年汇总，当前只有 1 行。加工分两步：先把订单表 `ods_trade_order_di` 里 `pay_time` 非空（已支付）的订单，按 `YEAR(order_time)` 和 `buyer_id` 分组，用 `COUNT(*)` 算出每个买家当年的支付订单数 `cnt`；再按年份把这些买家汇总。

外层每个年份算三件事：购买用户数用 `COUNT(*)`；复购用户数用 `SUM(cnt >= 2)`，即当年支付订单数达到 2 单及以上的买家数；复购率用复购用户数除以购买用户数，`ROUND(..., 4)` 保留四位小数。

```sql
SELECT y, COUNT(*), SUM(cnt >= 2), ROUND(SUM(cnt >= 2) / COUNT(*), 4)
FROM (SELECT YEAR(order_time) AS y, buyer_id, COUNT(*) AS cnt FROM ods_trade_order_di WHERE pay_time IS NOT NULL
      GROUP BY YEAR(order_time), buyer_id) t GROUP BY y
```

举个取数例子：要看"全年复购率"，直接读这 1 行即可。要注意复购的判定口径是"当年支付订单数 ≥ 2"，而不是跨年留存，分析时别和次年留存率混淆。

## 依赖与调度

`ads_user_repurchase_yearly` 依赖一张上游表，订单表 `ods_trade_order_di`。调度每日 05:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`ads_user_repurchase_yearly` 发布前跑四项检查：主键 / 粒度唯一，保证每个年份只有一行；关键字段非空；行数与前一天相比波动超过 30% 告警；可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

检查失败会直接影响下游。年份粒度重复会让购买用户数和复购用户数被重复累加，复购率失真；行数或买家数骤降往往是 `ods_trade_order_di` 没抽全，复购率随之偏离。看到告警先定位上游再发布。
