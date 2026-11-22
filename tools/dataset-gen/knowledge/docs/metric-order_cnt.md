# 指标说明：下单数（order_cnt）

## 计算公式

`order_cnt` 统计的是一段时间内发生的下单行为笔数，口径是 `COUNT(DISTINCT order_id)`，也就是对订单号去重后计数。这里要特别留意，同一个订单在明细表里往往会拆成多行（一个订单可能买了好几件商品），所以必须按 `order_id` 去重，不能直接数行数，否则会把一个订单重复计算。时间归属看下单日期 `order_date`，哪天下单就算到哪天。

因为用的是去重计数，`order_cnt` 不是一个简单可加的量：同一个订单可能跨多行，按天汇总时各天内部已经去过重，但如果你想把几天、几周的 `order_cnt` 直接相加得到一个更长周期的下单数，是不对的——跨天相加会把同一订单在不同天的去重结果机械叠加，得到的只是各期之和而非真正的去重下单数。要算更长周期，应该回到对应粒度的汇总表或明细重新按 `order_id` 去重。

## 取哪张表

按统计周期和维度选表是取数的第一步。看单天就用带 `_1d` 后缀的表，看整周用 `_1w`，看整月用 `_1m`。

- 品牌维度：日用 `dws_trade_brand_order_1d`，周用 `dws_trade_brand_order_1w`，月用 `dws_trade_brand_order_1m`。
- 一级品类维度：日用 `dws_trade_cate1_order_1d`，周用 `dws_trade_cate1_order_1w`，月用 `dws_trade_cate1_order_1m`。
- 二级品类维度：日用 `dws_trade_cate2_order_1d`，周用 `dws_trade_cate2_order_1w`，月用 `dws_trade_cate2_order_1m`。
- 会员等级维度：日用 `dws_trade_level_order_1d`，周用 `dws_trade_level_order_1w`，月用 `dws_trade_level_order_1m`。

选表时记得对齐粒度，别用日表自己加总去凑月度去重数。

## 来源与历史变化

数据溯源要一直追到明细。`order_cnt` 的源头是下单明细表 `dwd_trade_order_detail_di`，上面各张 `dws` 汇总表都是在它之上按不同维度聚合出来的。历史上这个指标自上线以来未调整计算规则，一直是按 `order_id` 去重计数，老数据和新数据的口径一致，可以放心做趋势对比。
