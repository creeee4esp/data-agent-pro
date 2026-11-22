# 表说明：dwd_trade_order_detail_di（DWD-交易订单明细宽表）

## 概述

`dwd_trade_order_detail_di` 是交易的核心明细宽表，属于 DWD 层，表注释为 DWD-交易订单明细宽表，当前 35,138 行，一条订单明细一行。它把订单、买家、商品、品牌、两级品类都拍平在一起，由多张贴源表关联而成，是 GMV、品类销售、省份销售、转化等众多汇总的共同源头。给新同事的定位是：交易侧凡是现成汇总不够用、需要灵活下钻的，都从这张宽表出发；但也正因为它是明细，统计已支付口径时一定要用 `is_paid` 过滤。

## 字段

- `id` 是 bigint 的明细ID，是本表主键。
- `order_id` 是 bigint 的订单ID，一个订单可能有多条明细。
- `buyer_id` 是 bigint 的买家用户ID。
- `member_level` 是 tinyint 的买家会员等级，为下单时随买家带入。
- `receiver_province` 是 varchar(20) 的收货省份，按收货地而非常住地。
- `sku_id` 是 bigint 的商品SKU ID。
- `sku_name` 是 varchar(100) 的商品名称。
- `brand_name` 是 varchar(50) 的品牌。
- `category2_id` 是 int 的二级品类ID。
- `category2_name` 是 varchar(50) 的二级品类名称。
- `category1_id` 是 int 的一级品类ID。
- `category1_name` 是 varchar(50) 的一级品类名称，品类层级已拍平可直接聚合。
- `qty` 是 int 的购买件数，含未支付明细，算已支付件数要配合 `is_paid`。
- `unit_price` 是 decimal(10,2) 的成交单价，单位元，是下单时的实际单价。
- `item_amount` 是 decimal(12,2) 的明细金额，单位元，GMV 和销售额都基于已支付的它累加。
- `order_status` 是 varchar(20) 的订单状态。
- `is_paid` 是 tinyint 的是否已支付，1 表示是、0 表示否，所有已支付口径都靠它过滤。
- `order_time` 是 datetime 的下单时间。
- `order_date` 是 date 的下单日期，是业务时间。
- `dt` 是 date 的分区日期，用于分区裁剪，别当下单日期用。

## 上下游

`dwd_trade_order_detail_di` 的上游是 `ods_trade_order_item_di`、`ods_trade_order_di`、`ods_user_info_di`、`ods_item_sku_di` 和 `ods_item_category_di`。它的下游非常多，面向 ADS 的 `ads_trade_cate1_sales_monthly`、`ads_trade_gmv_monthly`、`ads_trade_province_sales_monthly`、`ads_traffic_conversion_daily`，以及品牌、一级/二级品类、会员等级等粒度的日/周/月 DWS 汇总，如 `dws_trade_brand_order_1d`、`dws_trade_cate1_order_1m`、`dws_trade_level_order_1d` 等。

## 使用注意

`dwd_trade_order_detail_di` 适合做交易侧的灵活下钻，比如按品牌、二级品类、会员等级、收货省份统计销售额和件数，用 `is_paid = 1` 过滤已支付明细，用 `item_amount` 累加金额。固定口径的 GMV、品类销售、省份销售建议直接用对应 ADS 表，避免重复计算也避免口径走偏。注意区分 `order_date`（业务日期）与 `dt`（分区日期），`qty` 含未支付件数。本家族有历史归档表 `dwd_trade_order_detail_di_his`、带日期备份 `dwd_trade_order_detail_di_bak_20250205` 等和测试表 `test_dwd_trade_order_detail_di`，都是旧快照、时点副本或调试产物，统计一律用 `dwd_trade_order_detail_di`。
