# 取数需求单 REQ-2025-0115

## 需求

运营部想拉一份 2025-01、各仓库按周的发货单数 `ship_cnt`，不加筛选，用来看看年初各仓的发货量铺得怎么样。

## 结论

`dws_lgs_warehouse_ship_1w` 是仓库粒度的按周发货汇总表，拿它来做刚好。字段取 `stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，按 `stat_date` 限定在 2025-01 的周区间。

口径上可以放心加：发货单数 `ship_cnt` 和已签收单数 `signed_cnt` 都是可以跨行相加的，把某个仓多周累加、或者把各仓汇成大盘总量，都没问题。但平均发货时长 `avg_ship_hours` 不能相加，想看跨仓的整体时效得按 `ship_cnt` 加权，别直接平均。

这次已经按一次性取数交付运营部了。
