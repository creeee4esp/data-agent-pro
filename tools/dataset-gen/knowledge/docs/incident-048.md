# 数据事故报告：2025-12-11 dwd_trade_order_detail_di 关键字段为空

## 现象

`dwd_trade_order_detail_di`（DWD-交易订单明细宽表）于 2025-12-11 出现关键字段为空，要点如下：

- 发现时间：2025-12-11
- 表现：交易订单明细宽表中关键字段出现空值，下游关联与拆分口径受影响
- 影响持续时长：6 小时

## 原因与影响范围

`dwd_trade_order_detail_di` 本次问题的核心结论汇总如下：

- 原因：上游业务系统字段改名，本表仍按旧字段取数而落空
- 受影响下游：共 10 张表，分别为 `ads_trade_cate1_sales_monthly`、`ads_trade_gmv_monthly`、`ads_trade_province_sales_monthly`、`ads_traffic_conversion_daily`、`dws_trade_brand_order_1d`、`dws_trade_brand_order_1m`、`dws_trade_brand_order_1w`、`dws_trade_cate1_order_1d`、`dws_trade_cate1_order_1m`、`dws_trade_cate1_order_1w`
- 处理方式：修复 SQL 后回刷近 7 天
- 补数情况：部分补数，剩余部分仍在跟进
- 责任归属：流量数据组
