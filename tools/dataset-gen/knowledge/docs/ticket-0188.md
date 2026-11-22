# 取数需求单 REQ-2025-0188

## 需求

市场部于 2025-08-13 提单，要点如下：

- 对象：一级品类粒度的总停留秒数 `stay_seconds`
- 周期：按月汇总
- 报告期：2025-08
- 筛选：无
- 用途：长期跟踪，请求固化为报表

## 结论

`dws_traffic_cate1_visit_1m` 可满足本需求，要点如下：

- 取数字段：`stat_date`、`category1_id`、`category1_name`、`pv`、`uv`、`stay_seconds`，按 `stat_date` 限定 2025-08
- 可加和项：`pv` 与 `stay_seconds` 可跨行相加，按品类或按月汇总均成立
- 注意：`uv` 为去重访客数，不能累加，整体 `uv` 须回源重算
- 交付：已交付，并沉淀为固定报表
