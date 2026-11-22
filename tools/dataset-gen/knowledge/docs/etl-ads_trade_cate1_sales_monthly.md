# 加工说明：ads_trade_cate1_sales_monthly

## 加工逻辑

`ads_trade_cate1_sales_monthly` 存的是 ADS-一级品类月度销售额，按"下单月份 + 一级品类"把销售汇总起来，现在一共 84 行。简单说，就是回答"某个月某个一级品类卖了多少钱、卖了多少件"这类问题的一张表。

加工从交易订单明细宽表 `dwd_trade_order_detail_di` 取数，用 `DATE_FORMAT(order_date, '%Y-%m')` 把下单日期切成月份，再和 `category1_name` 一起分组。这里有个关键点要跟新同事说清楚：销售额和销量都只统计已支付的部分。SQL 里用 `SUM(IF(is_paid = 1, item_amount, 0))` 算销售额，用 `SUM(IF(is_paid = 1, qty, 0))` 算销量，也就是 `is_paid` 不等于 1 的明细行金额和数量都按 0 处理。

```sql
SELECT DATE_FORMAT(order_date, '%Y-%m'), category1_name,
  SUM(IF(is_paid = 1, item_amount, 0)), SUM(IF(is_paid = 1, qty, 0))
FROM dwd_trade_order_detail_di GROUP BY DATE_FORMAT(order_date, '%Y-%m'), category1_name
```

## 依赖与调度

`ads_trade_cate1_sales_monthly` 只依赖一张上游表，交易订单明细宽表 `dwd_trade_order_detail_di`。调度上每天 05:00 开始，等上游全部成功后才运行，处理前一天（T-1）的数据。所以你早上看到的结果，覆盖的是截至昨天的订单。

## 质量检查

`ads_trade_cate1_sales_monthly` 发布前要过四项检查：主键 / 粒度唯一，保证"月份 + 一级品类"不重复；关键字段非空；行数与前一天相比波动超过 30% 告警；可加和指标总额与来源明细对账（DWS 必须等于 DWD）。

这几项失败都会往下游传。粒度重复会让某个品类的销售额和销量被重复累加，各品类占比随之算错；行数骤降通常意味着 `dwd_trade_order_detail_di` 没抽全，直接用会低估品类销售。碰到告警先回查上游，别带病对外发布。
