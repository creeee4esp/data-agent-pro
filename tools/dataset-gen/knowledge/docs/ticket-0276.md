# 取数需求单 REQ-2025-0276

## 需求

客服部 2025-11-23 提的，想要 2025-04 的总停留秒数（`stay_seconds`），按访问渠道粒度、月度访问口径拉一份，没有额外筛选。单号 REQ-2025-0276，数据平台组接的。

## 结论

`dws_traffic_vchannel_visit_1m`（DWS-访问渠道粒度访问汇总（月））这张表够用，字段用 `stat_date`、`channel`、`pv`、`uv`、`stay_seconds`，日期 `stat_date` 取 2025-04。

加和这块：`stay_seconds` 可跨行相加，各渠道的 `stay_seconds` 加起来算整体停留时长没问题，`pv` 也能加。`uv` 是去重访客数，不能跨行相加，别把各渠道的 `uv` 求和。

这次已交付，附 SQL 供需求方自助复用。
