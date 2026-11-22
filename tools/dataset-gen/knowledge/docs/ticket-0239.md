# 取数需求单 REQ-2025-0239

## 需求

物流部 2025-10-10 提的，想要 2025-02 的总停留秒数（`stay_seconds`），按一级品类粒度、月度访问口径拉一份，没有额外筛选。单号 REQ-2025-0239，交易数据组接的。

## 结论

`dws_traffic_cate1_visit_1m`（DWS-一级品类粒度访问汇总（月））这张表够用，字段用 `stat_date`、`category1_id`、`category1_name`、`pv`、`uv`、`stay_seconds`，日期 `stat_date` 取 2025-02。

加和这块提醒一下：`stay_seconds` 可跨行相加，各品类的 `stay_seconds` 加起来算整体停留时长没问题，`pv` 也能加。但 `uv` 是去重访客数，不能跨行相加，别把各品类的 `uv` 求和，会重复算人。

这次已交付，附 SQL 供需求方自助复用。
