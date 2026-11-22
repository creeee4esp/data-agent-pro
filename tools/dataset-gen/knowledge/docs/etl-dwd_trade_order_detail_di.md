# 加工说明：dwd_trade_order_detail_di

## 加工逻辑

`dwd_trade_order_detail_di` 为 DWD-交易订单明细宽表，粒度为订单行，当前 35,138 行。加工以订单明细表 `ods_trade_order_item_di` 为主表，关联订单表 `ods_trade_order_di`（按 `order_id`）获取买家、收货省份与订单状态，并左连接用户表 `ods_user_info_di` 取会员等级、左连接商品SKU表 `ods_item_sku_di` 取商品名与品牌。

品类通过商品品类表 `ods_item_category_di` 两次左连接补齐：先以 SKU 的 `category_id` 命中二级品类，再以其 `parent_id` 回连得到一级品类，从而在同一行同时输出两级品类的编码与名称。是否支付由 `IF(o.pay_time IS NULL, 0, 1)` 派生，下单日期取 `DATE(o.order_time)`，分区 `dt` 沿用上游。

```sql
SELECT i.id, i.order_id, o.buyer_id, u.member_level, o.receiver_province,
       i.sku_id, s.sku_name, s.brand_name,
       c2.category_id, c2.category_name, c1.category_id, c1.category_name,
       i.qty, i.unit_price, i.item_amount, o.order_status,
       IF(o.pay_time IS NULL, 0, 1), o.order_time, DATE(o.order_time), i.dt
FROM ods_trade_order_item_di i
JOIN ods_trade_order_di o ON o.order_id = i.order_id
LEFT JOIN ods_user_info_di u ON u.user_id = o.buyer_id
LEFT JOIN ods_item_sku_di s ON s.sku_id = i.sku_id
LEFT JOIN ods_item_category_di c2 ON c2.category_id = s.category_id
LEFT JOIN ods_item_category_di c1 ON c1.category_id = c2.parent_id
```

本表以订单行为粒度，金额字段 `item_amount` 为行级金额，是否支付以 `pay_time` 是否为空判定，构成下游交易口径的基础。

## 依赖与调度

`dwd_trade_order_detail_di` 依赖五张上游表：商品品类表 `ods_item_category_di`、商品SKU表 `ods_item_sku_di`、订单表 `ods_trade_order_di`、订单明细表 `ods_trade_order_item_di` 与用户表 `ods_user_info_di`。调度每日 02:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dwd_trade_order_detail_di` 发布前执行三项检查：主键 / 粒度唯一，确保订单行 `id` 不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查未通过将影响下游。订单行粒度重复会使 GMV、销量被重复计算，各类销售汇总随之偏高；品类、会员等级等字段为空会导致按这些维度汇总的报表丢失分组；行数异常下降通常表明上游订单未抽取完整，下游销售规模被低估。本表为多张 ADS 报表的共同数据源，出现告警应先核查上游后再行发布。
