# 加工说明：dws_trade_brand_order

## 加工逻辑

`dws_trade_brand_order` 系列做的事情，是把订单明细按品牌汇到一起看下单情况。数据来自 `dwd_trade_order_detail_di`，每天跑一遍，产出日、周、月三张表。日表的逻辑是按 `order_date` 和 `brand_name` 分组，算出下单数 `COUNT(DISTINCT order_id)`、商品件数 `SUM(qty)`，以及已支付金额 `SUM(IF(is_paid = 1, item_amount, 0))`：

```sql
INSERT INTO dws_trade_brand_order_1d
SELECT order_date, brand_name,
       COUNT(DISTINCT order_id), SUM(qty),
       SUM(IF(is_paid = 1, item_amount, 0))
FROM dwd_trade_order_detail_di
GROUP BY order_date, brand_name
```

这里要提醒新同事两点：一是下单数用的是 `COUNT(DISTINCT order_id)`，因为一个订单可能包含同品牌多条明细，不去重会虚高；二是金额只统计 `is_paid = 1` 的那部分，所以它是"已支付金额"，不是下单总额，别跟 GMV 混为一谈。周表 `dws_trade_brand_order_1w` 把日期用 `DATE_SUB(order_date, INTERVAL WEEKDAY(order_date) DAY)` 归到周一，月表 `dws_trade_brand_order_1m` 用 `DATE_FORMAT(order_date, '%Y-%m-01')` 归到月初，口径都一样。三张表现在大约是 4,213 行（日）、636 行（周）、144 行（月）。

## 依赖与调度

`dws_trade_brand_order` 只有一个上游，就是订单明细宽表 `dwd_trade_order_detail_di`。调度上每天 03:30 开始，等上游全部成功后才跑，处理的是前一天（T-1）的数据。

## 质量检查

`dws_trade_brand_order` 落表后会跑四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，还有可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账看的是件数、金额这些能加的指标，下单数因为做了去重不参与简单加和对账。哪一项挂了，下游看品牌的下单看板就会跟着错，件数或已支付金额对不上。
