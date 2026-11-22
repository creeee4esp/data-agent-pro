# 取数需求单 REQ-2025-0157

## 需求

商品部于 2025-07-05 提单，要点如下：

- 对象：一级品类粒度的总停留秒数 `stay_seconds`
- 周期：按周汇总
- 报告期：2025-05
- 筛选：无

## 结论

`dws_traffic_cate1_visit_1w` 可满足本需求，要点如下：

- 取数字段：`stat_date`、`category1_id`、`category1_name`、`pv`、`uv`、`stay_seconds`，按 `stat_date` 限定 2025-05
- 口径提示：`pv`、`stay_seconds` 可跨行相加，按品类或按周汇总均成立
- 注意：`uv` 为去重访客数，不能跨行累加，整体 `uv` 须回源重算
- 交付：已交付一次性取数结果
