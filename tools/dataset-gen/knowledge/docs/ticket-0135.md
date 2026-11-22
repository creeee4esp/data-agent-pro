# 取数需求单 REQ-2025-0135

## 需求

财务部需 2025-03、按访问用户粒度的周度总停留秒数 `stay_seconds`，无筛选，用于用户停留时长分布的观察。

- 时间范围：2025-03
- 维度：访问用户
- 核心指标：总停留秒数 `stay_seconds`

## 结论

`dws_traffic_visitor_visit_1w` 对口，为访问用户粒度的按周访问汇总表。

- 取数字段：`stat_date`、`visitor_id`、`pv`、`uv`、`stay_seconds`
- 时间范围：`stat_date` 限定 2025-03
- 可加：`stay_seconds`、`pv` 可跨行相加，按用户跨周累加成期间总停留成立
- 注意：`uv` 不可相加，同一访客跨周会重复计数
- 交付：已交付，附 SQL 供需求方自助复用
