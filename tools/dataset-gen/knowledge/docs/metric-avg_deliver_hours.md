# 指标说明：平均配送时长（小时，已签收）（avg_deliver_hours）

## 计算公式

`ads_lgs_delivery_monthly` 里的平均配送时长是一个平均值类指标。

- 口径：`ROUND(AVG(deliver_hours), 2)`，对配送时长取平均并保留两位小数，单位小时。
- 时间归属：按 `DATE_FORMAT(ship_date, '%Y-%m')` 归到发货月。
- 拆分维度：按仓库区域 `warehouse_region` 分组。
- 可加和：否。平均值不能跨行、跨月相加或再平均，更长周期要回明细重算。

## 取哪张表

查平均配送时长只有一张表。

- 月度口径：`ads_lgs_delivery_monthly`，按发货月和仓库区域给出。

## 来源与历史变化

平均配送时长的报表加工回溯到发货明细表 `dwd_lgs_shipment_di`。

- 加工 SQL：`INSERT INTO ads_lgs_delivery_monthly SELECT DATE_FORMAT(ship_date, '%Y-%m'), warehouse_region, COUNT(*), ROUND(AVG(ship_hours), 2), ROUND(AVG(deliver_hours), 2) FROM dwd_lgs_shipment_di GROUP BY DATE_FORMAT(ship_date, '%Y-%m'), warehouse_region`。
- 历史变更：2025-12-28 由交易数据组发布说明——平均配送时长只统计已签收的物流单。
