# 取数需求单 REQ-2025-0137

## 需求

客服部需 2025-03 至 2025-05、收货省份为四川省的按月已签收单数 `signed_cnt`，筛选只看四川省，用于该省履约签收的季度跟踪。

- 时间范围：2025-03 至 2025-05
- 维度：收货省份（只看四川省）
- 核心指标：已签收单数 `signed_cnt`

## 结论

`dws_lgs_prov_ship_1m` 对口，为收货省份粒度的按月发货汇总表。

- 取数字段：`stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`
- 过滤：`receiver_province` 等于四川省，`stat_date` 限定 2025-03 至 2025-05
- 可加：`signed_cnt`、`ship_cnt` 可跨行相加，四川省按月累加成季度签收量成立
- 注意：`avg_ship_hours` 不可相加，须按 `ship_cnt` 加权
- 交付：已交付，并沉淀为固定报表
