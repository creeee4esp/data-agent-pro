# 取数需求单 REQ-2025-0189

## 需求

商品部于 2025-08-16 提单，要点如下：

- 对象：访问渠道粒度的总停留秒数 `stay_seconds`
- 周期：按周汇总
- 报告期：2025-06
- 筛选：无

## 结论

`dws_traffic_vchannel_visit_1w` 可满足本需求，要点如下：

- 取数字段：`stat_date`、`channel`、`pv`、`uv`、`stay_seconds`，按 `stat_date` 限定 2025-06
- 可加和项：`pv` 与 `stay_seconds` 可跨行相加，按渠道或按周汇总均成立
- 注意：`uv` 为去重访客数，不能累加，整体 `uv` 须回源重算
- 交付：已交付一次性取数结果
