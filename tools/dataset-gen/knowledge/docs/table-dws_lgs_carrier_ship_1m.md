# 表说明：dws_lgs_carrier_ship_1m（DWS-承运商粒度发货汇总（月））

## 概述

`dws_lgs_carrier_ship_1m` 按承运商和月汇总发货，属 DWS 层，表注释为 DWS-承运商粒度发货汇总（月），当前 52 行。粒度为统计月 × 承运商，`stat_date` 用月初日期表示。上游为 `dwd_lgs_shipment_di`。用于看承运商月度发货与签收。

## 字段

- `stat_date`：date，统计月，用月初日期表示，如 2025-06-01。
- `carrier`：varchar(20)，承运商，除月份外的拆分维度。
- `ship_cnt`：int，发货单数，可跨月相加。
- `signed_cnt`：int，已签收单数，可跨月相加，作签收率分子。
- `avg_ship_hours`：decimal(10,2)，平均发货时长（小时），月内均值，跨月勿直接再平均。

## 上下游

`dws_lgs_carrier_ship_1m` 数据来自 `dwd_lgs_shipment_di`，按发货月归至月初并按承运商分组汇总。无下游表，供分析按需使用。

## 使用注意

- 适合：承运商月度发货量、签收率、发货时效对比。
- 签收率：`signed_cnt / ship_cnt`；跨月要分别累加再相除。
- 不适合：按省份、仓库拆分；不含配送时长。
- 噪声表：备份表 `dws_lgs_carrier_ship_1m_bak`、测试表 `test_dws_lgs_carrier_ship_1m`，为时点副本或调试产物，不能用于统计。
- 口径：统计一律用 `dws_lgs_carrier_ship_1m`。
