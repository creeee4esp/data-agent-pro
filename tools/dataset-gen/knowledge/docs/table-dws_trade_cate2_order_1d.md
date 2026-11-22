# 表说明：dws_trade_cate2_order_1d（DWS-二级品类粒度下单汇总（日））

## 概述

`dws_trade_cate2_order_1d` 按统计日期和二级品类汇总每日下单情况，当前 5,078 行。数据来自 `dwd_trade_order_detail_di`，下单数去重、件数与已支付金额求和。需要注意两点：`stat_date` 是天粒度，别和周月表混用；`item_qty` 含未支付订单，和只算已支付的 `paid_amount` 分母不同，不要直接相除当件单价。

## 字段

- `stat_date`：统计日期，按天，取某天用等值过滤，取区间用 `BETWEEN`。
- `category2_id`：二级品类ID，和 `stat_date`、`category2_name` 共同构成行粒度，关联聚合建议用 ID。
- `category2_name`：二级品类名称，展示和排序常用，注意不同一级品类下可能有重名的二级品类，分组时最好带上 ID。
- `order_cnt`：下单数，按订单去重计数，可跨天相加。
- `item_qty`：购买件数，含未支付订单，口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分，不是全部下单金额。

## 上下游

来源是交易明细表 `dwd_trade_order_detail_di`，按 `order_date`、`category2_id`、`category2_name` 分组聚合写入，当前没有登记下游表。更高层汇总一般直接在本表上再聚合。

## 使用注意

适合做二级品类的每日下单趋势、日区间汇总和二级品类间对比。不适合一级品类、SKU 或用户维度拆解，请改用对应粒度的表。同家族的噪声表有 6 张带日期备份（如 `dws_trade_cate2_order_1d_bak_20250216`、`dws_trade_cate2_order_1d_bak_20250217`）和一张旧版本 `dws_trade_cate2_order_1d_old`。备份是历史快照、旧版本是改版前的遗留，数据可能残缺或口径不同，统计一律用本表，这是最容易取错的地方。
