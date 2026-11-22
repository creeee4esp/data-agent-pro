# 取数需求单 REQ-2025-0158

## 需求

会员中心于 2025-07-05 提出的需求，围绕仓库维度的发货单数 `ship_cnt` 展开，按周汇总，报告期为 2025-05，不设筛选条件。从背景看，会员中心希望把握当月各仓库的发货节奏，用作与会员履约体验相关的侧面观察。

## 结论

`dws_lgs_warehouse_ship_1w` 承载了这一诉求，取数落在 `stat_date`、`warehouse_id`、`warehouse_region`、`ship_cnt`、`signed_cnt`、`avg_ship_hours` 这几个字段上，以 `stat_date` 限定 2025-05 后按仓库与周铺开即可。分析时要区分两类指标的口径：`ship_cnt` 与 `signed_cnt` 是可加和指标，跨周或跨仓库求和都成立；而 `avg_ship_hours` 是平均发货时长，不能跨行直接相加，若要得到整体平均须按发货单数加权重算。本次已交付，并随附 SQL 供会员中心自助复用。
