# 取数需求单 REQ-2025-0203

## 需求

会员中心 2025-08-25 提单：访问用户粒度浏览量 `pv`，按周汇总，报告期 2025-08，无筛选。

## 结论

`dws_traffic_visitor_visit_1w` 命中需求。取数字段：`stat_date`、`visitor_id`、`pv`、`uv`、`stay_seconds`；按 `stat_date` 卡 2025-08。口径：`pv`、`stay_seconds` 可跨行相加；`uv` 是去重访客数，不能累加，整体 `uv` 须回源重算。本次已交付一次性取数结果。
