# 取数需求单 REQ-2025-0112

## 需求

客服部需 2025-01、按收货省份看每月的平均发货时长 `avg_ship_hours`（小时），无筛选，用于各省时效的差异排查。

- 时间范围：2025-01
- 维度：收货省份
- 核心指标：平均发货时长 `avg_ship_hours`

## 结论

`dws_lgs_prov_ship_1m` 对口，为收货省份粒度的按月发货汇总表。

- 取数字段：`stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`
- 时间范围：`stat_date` 限定 2025-01
- 关键口径：`avg_ship_hours` 不可跨行相加，跨省不能直接平均，须用 `ship_cnt` 加权
- 可加：`ship_cnt`、`signed_cnt` 可跨行相加
- 交付：已交付，附 SQL 供需求方自助复用
