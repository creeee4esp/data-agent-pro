# 表说明：dws_traffic_sku_visit_1w（DWS-商品SKU 粒度访问汇总（周））

## 概述

`dws_traffic_sku_visit_1w` 是 DWS 层的商品 SKU 周度访问汇总表。

- 粒度：一周 + 一个 SKU 一行。
- 周口径：以周一为一周起点。
- 当前规模：15,625 行。
- 适用场景：SKU 的周度流量观察与趋势对比。

## 字段

- `stat_date`（date）：统计周，存的是该周周一的日期，按周筛选要用周一日期。
- `sku_id`（bigint）：商品 SKU ID，与 `stat_date` 共同唯一确定一行。
- `pv`（int）：该 SKU 当周浏览量，为访问次数累计，不去重。
- `uv`（int）：该 SKU 当周访客数，已去重，不能跨周直接相加当作去重人数。
- `stay_seconds`（bigint）：该 SKU 当周总停留秒数，单位为秒，算人均需自行除以 `uv`。

## 上下游

`dws_traffic_sku_visit_1w` 的上游是明细层的 `dwd_traffic_visit_di`。

- 加工逻辑：按周一日期和 `sku_id` 分组，`pv` 取记录数，`uv` 取去重访客数，`stay_seconds` 取停留时间之和。
- 下游：暂无登记的下游表，可直接供分析取用。

## 使用注意

`dws_traffic_sku_visit_1w` 适合做 SKU 的周度排行、周环比和停留时长分析。

- 不适合：日粒度或月粒度的拆解，应改用对应粒度的汇总表。
- 噪声表：备份表 `dws_traffic_sku_visit_1w_bak`、旧版本 `dws_traffic_sku_visit_1w_v2`、测试表 `test_dws_traffic_sku_visit_1w`。
- 为什么不能用：备份是历史快照，旧版本是早期口径，测试表是调试数据，口径与时效都不可靠。
- 正式取数只认 `dws_traffic_sku_visit_1w`。
