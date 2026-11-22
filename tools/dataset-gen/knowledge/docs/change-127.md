# 变更公告：新表上线（dws_traffic_sku_visit_1m）

## 变更说明

`dws_traffic_sku_visit_1m` 新表上线，表注释为“DWS-商品SKU 粒度访问汇总（月）”，由用户数据组发布，2025-10-26 生效。

该表上游为 `dwd_traffic_visit_di`，按商品SKU、月粒度汇总访问情况，可用于SKU 维度的月度流量分析。

影响范围为需要SKU 月度访问数据的报表与建模场景。使用方自生效日起可接入 `dws_traffic_sku_visit_1m` 取数，注意粒度为商品SKU × 月；上游 `dwd_traffic_visit_di` 异常时本表产出可能延迟，请关注依赖状态。
