# 取数需求单 REQ-2025-0117

## 需求

市场部需 2025-01、各承运商按日的平均发货时长 `avg_ship_hours`（小时），无筛选，用于承运商时效对比。

- 时间范围：2025-01
- 维度：承运商
- 核心指标：平均发货时长 `avg_ship_hours`

## 结论

`dws_lgs_carrier_ship_1d` 对口，为承运商粒度的按日发货汇总表。

- 取数字段：`stat_date`、`carrier`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`
- 时间范围：`stat_date` 限定 2025-01
- 关键口径：`avg_ship_hours` 不可跨行相加，跨承运商或跨日不能直接平均，须用 `ship_cnt` 加权
- 可加：`ship_cnt`、`signed_cnt` 可跨行相加
- 交付：已交付，并沉淀为固定报表
