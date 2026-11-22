# 指标说明：销售额：已支付明细金额（元）（sales_amount）

## 计算公式

`ads_trade_cate1_sales_monthly` 里的销售额口径是对已支付明细金额求和。

- 口径：`SUM(IF(is_paid = 1, item_amount, 0))`，只累加已支付明细行。
- 时间归属：按 `DATE_FORMAT(order_date, '%Y-%m')` 归到下单月。
- 拆分维度：按一级品类 `category1_name` 分组。
- 可加和：否。报表已按月、按品类加工好，按报表口径取用，不要跨维度叠加。

## 取哪张表

查销售额直接取报表。

- 按一级品类看月度销售额：`ads_trade_cate1_sales_monthly`。
- 按省份看月度销售额：`ads_trade_province_sales_monthly`。

两张表分别是品类口径和省份口径，不要把它们的结果相加。

## 来源与历史变化

销售额的报表加工回溯到下单明细表 `dwd_trade_order_detail_di`。

- 加工 SQL：`INSERT INTO ads_trade_cate1_sales_monthly SELECT DATE_FORMAT(order_date, '%Y-%m'), category1_name, SUM(IF(is_paid = 1, item_amount, 0)), SUM(IF(is_paid = 1, qty, 0)) FROM dwd_trade_order_detail_di GROUP BY DATE_FORMAT(order_date, '%Y-%m'), category1_name`。
- 历史变更：2025-06-29 由商品数据组发布说明——销售额与 GMV 计算规则相同，按一级品类或省份拆分。
