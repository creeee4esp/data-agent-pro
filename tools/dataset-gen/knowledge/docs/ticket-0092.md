# 取数需求单 REQ-2025-0092

## 需求

市场部需 2025-04、各商品 SKU 按周的已支付明细金额 `paid_amount`（元），无筛选，用于单品成交金额排名。

- 时间范围：2025-04
- 维度：商品 SKU
- 核心指标：已支付明细金额 `paid_amount`

## 结论

`dws_trade_sku_order_1w` 对口，为商品 SKU 粒度的按周下单汇总表。

- 取数字段：`stat_date`、`sku_id`、`order_cnt`、`item_qty`、`paid_amount`
- 时间范围：`stat_date` 限定 2025-04
- 口径：`paid_amount`、`item_qty` 可跨行相加，跨周或多 SKU 汇总均可
- 注意：`order_cnt` 不可相加，如需单量规模须逐行看
- 交付：已交付一次性取数结果
