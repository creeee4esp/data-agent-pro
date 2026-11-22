# 取数需求单 REQ-2025-0169

## 需求

物流部于 2025-07-21 提单，要点如下：

- 对象：一级品类粒度的访客数 `uv`
- 周期：按周汇总
- 报告期：2025-04
- 筛选：无

## 结论

`dws_traffic_cate1_visit_1w` 可满足本需求，要点如下：

- 取数字段：`stat_date`、`category1_id`、`category1_name`、`pv`、`uv`、`stay_seconds`，按 `stat_date` 限定 2025-04
- 关键口径：`uv` 为去重访客数，不能跨行累加，跨周或跨品类的整体 `uv` 须回源重算
- 可加和项：`pv` 与 `stay_seconds` 可直接相加
- 交付：已交付，附 SQL 供物流部自助复用
