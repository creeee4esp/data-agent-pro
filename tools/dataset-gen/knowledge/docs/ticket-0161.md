# 取数需求单 REQ-2025-0161

## 需求

物流部于 2025-07-11 正式提出取数申请，需求对象为仓库粒度的已签收单数 `signed_cnt`，按日汇总，报告期区间为 2025-02 至 2025-03，不附加任何筛选条件。该数据拟用于分析年初两个月各仓库的签收完成情况。

## 结论

`dws_lgs_warehouse_ship_1d` 为本需求推荐用表，取数涉及 `stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours` 等字段，以 `stat_date` 限定 2025-02 至 2025-03 后按仓库输出即可。需提请注意：`ship_cnt` 与 `signed_cnt` 为可加和指标，跨日或跨仓库求和均成立；而 `avg_ship_hours` 系平均发货时长，不得跨行直接相加，整体平均须按发货单数加权重算。本需求已交付一次性取数结果。
