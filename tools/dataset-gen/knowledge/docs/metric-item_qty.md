# 指标说明：购买件数（item_qty）

## 计算公式

购买件数的口径是 `SUM(qty)`，即对明细行的购买数量求和。

- 聚合方式：直接累加 `qty`，不去重。
- 时间归属：按下单日期 `order_date` 归属，哪天下单算到哪天。
- 可加和：可以。各行、各天、各周可直接相加，日汇总求和即为周、月结果。
- 口径范围：含未支付订单，统计的是下单动作产生的件数。

## 取哪张表

按统计周期选后缀：日用 `_1d`，周用 `_1w`，月用 `_1m`。

- 品牌维度：`dws_trade_brand_order_1d` / `dws_trade_brand_order_1w` / `dws_trade_brand_order_1m`。
- 一级品类维度：`dws_trade_cate1_order_1d` / `dws_trade_cate1_order_1w` / `dws_trade_cate1_order_1m`。
- 二级品类维度：`dws_trade_cate2_order_1d` / `dws_trade_cate2_order_1w` / `dws_trade_cate2_order_1m`。
- 会员等级维度：`dws_trade_level_order_1d` / `dws_trade_level_order_1m`。
- 月度一级品类销售口径：`ads_trade_cate1_sales_monthly`，该表给出的是已支付件数，口径与 `dws` 不同。

## 来源与历史变化

购买件数来自下单明细表 `dwd_trade_order_detail_di`，上面各汇总表均由它聚合而来。

- 历史变更：2025-10-28 由交易数据组发布说明——购买件数含未支付订单，统计已支付件数请用 ADS 品类报表。
- 取数提醒：要已支付件数时改用 `ads_trade_cate1_sales_monthly`，不要直接拿 `item_qty`。
