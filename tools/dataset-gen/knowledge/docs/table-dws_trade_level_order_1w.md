# 表说明：dws_trade_level_order_1w（DWS-会员等级粒度下单汇总（周））

## 概述

`dws_trade_level_order_1w` 按周、按会员等级汇总下单。

- 当前 265 行。
- `stat_date` 存周一日期，一周一行。
- 来源 `dwd_trade_order_detail_di`，口径与日表月表一致。
- 下单数去重，件数和已支付金额求和。

## 字段

- `stat_date`：统计周，取周一日期，查某周用该周周一日期匹配。
- `member_level`：会员等级，`tinyint` 编码值，是等级码不是名称。
- `order_cnt`：下单数，按订单去重，可跨周相加。
- `item_qty`：购买件数，含未支付订单，口径偏大。
- `paid_amount`：已支付订单的明细金额（元），只累计已支付部分。

## 上下游

- 来源：交易明细表 `dwd_trade_order_detail_di`。
- 加工：按周一日期和 `member_level` 分组聚合写入。
- 下游：当前无登记下游表。

## 使用注意

- 适合：会员等级的周走势、周区间汇总、各等级对比。
- 不适合：日粒度查询请用日表，跨月对比用月表。
- 同家族噪声表：备份 `dws_trade_level_order_1w_bak`、7 张带日期备份（如 `dws_trade_level_order_1w_bak_20250515`、`dws_trade_level_order_1w_bak_20250610`）、旧版本 `dws_trade_level_order_1w_v1`。
- 备份是历史快照、旧版本是改版遗留，数据可能过期或口径不同，统计一律用本表。
