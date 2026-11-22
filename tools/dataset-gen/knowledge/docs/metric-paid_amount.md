# 指标说明：已支付订单的明细金额（元）（paid_amount）

## 计算公式

`paid_amount` 的口径是 `SUM(IF(is_paid = 1, item_amount, 0))`，只累加已支付明细行的 `item_amount`，未支付行计 0。这里最容易误用的地方是把它当成「实收金额」：它不扣优惠、也不扣退款，只是已支付订单的明细金额之和。时间归属看下单日期 `order_date`，按下单日落账。该指标可加和，各行、各天、各周可以直接相加，日表求和即得更长周期的结果。

## 取哪张表

取数先按周期选后缀，日用 `_1d`、周用 `_1w`、月用 `_1m`。

- 品牌维度：`dws_trade_brand_order_1d` / `dws_trade_brand_order_1w` / `dws_trade_brand_order_1m`。
- 一级品类维度：`dws_trade_cate1_order_1d` / `dws_trade_cate1_order_1w` / `dws_trade_cate1_order_1m`。
- 二级品类维度：`dws_trade_cate2_order_1d` / `dws_trade_cate2_order_1w` / `dws_trade_cate2_order_1m`。
- 会员等级维度：`dws_trade_level_order_1d` / `dws_trade_level_order_1w` / `dws_trade_level_order_1m`。

常见的坑是跨维度相加：同一笔金额在品牌表和品类表里都会出现，两张表的结果不能相加，否则会重复计算，取数时只选一个维度。

## 来源与历史变化

`paid_amount` 来自下单明细表 `dwd_trade_order_detail_di`，上面各 `dws` 表都是按不同维度在它之上聚合得到。历史变更方面，2025-10-12 由物流数据组发布说明：`paid_amount` 为已支付订单的明细金额，不扣优惠和退款。做口径对齐时请以这条说明为准，涉及净收入分析时不要直接用它。
