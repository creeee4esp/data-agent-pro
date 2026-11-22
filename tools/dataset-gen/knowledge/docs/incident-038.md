# 数据事故报告：2025-10-10 dwd_traffic_visit_di 分区缺失

## 现象

`dwd_traffic_visit_di`（DWD-商品访问明细）于 2025-10-10 经监控发现分区缺失。核查确认，该表当天应生成的分区未能落地，商品访问明细出现空档，属分区缺失类事故。事故自发现起持续 2 小时。

## 原因与影响范围

`dwd_traffic_visit_di` 本次问题的根本原因为上游当天无数据未告警，上游空跑且未触发提醒，本表因无数据可算而缺失分区。

就影响范围而言，由于本表处于明细层，分区缺失向下传导，波及以下 10 张下游表：`ads_traffic_conversion_daily`、`dws_traffic_cate1_visit_1d`、`dws_traffic_cate1_visit_1m`、`dws_traffic_cate1_visit_1w`、`dws_traffic_sku_visit_1d`、`dws_traffic_sku_visit_1m`、`dws_traffic_sku_visit_1w`、`dws_traffic_vchannel_visit_1d`、`dws_traffic_vchannel_visit_1m`、`dws_traffic_vchannel_visit_1w`。

处理方面，数据平台组采取重跑当天任务并补数的方式进行修复。截至报告出具，相关数据已完成补数，上述下游表随之恢复正常。
