# 加工说明：dwd_traffic_visit_di

## 加工逻辑

`dwd_traffic_visit_di` 存的是 DWD-商品访问明细，一条访问一行，当前 95,138 行，是这批表里体量最大的一张。加工的思路很简单：把商品页访问日志 `ods_log_visit_di` 和商品维表 `dim_sku` 按 `sku_id` 关联，给每次访问补上品牌和一级品类，这样下游看流量时就能直接按品类切。

字段上，访问侧保留 `visit_id`、`visitor_id`、`sku_id`、`channel`、`stay_seconds`，从 `dim_sku` 带出 `brand_name`、`category1_id`、`category1_name`，访问日期用 `DATE(v.visit_time)`，分区 `dt` 沿用上游。

```sql
SELECT v.visit_id, v.visitor_id, v.sku_id, v.channel, v.stay_seconds,
  d.brand_name, d.category1_id, d.category1_name, DATE(v.visit_time), v.dt
FROM ods_log_visit_di v JOIN dim_sku d ON d.sku_id = v.sku_id
```

跟新同事提一句：这张表是算 UV 的基础，但它本身是访问明细，一个 `visitor_id` 一天可能有多条。想算 UV 要用 `COUNT(DISTINCT visitor_id)` 去重，别直接数行数。

## 依赖与调度

`dwd_traffic_visit_di` 依赖两张上游表：商品维表 `dim_sku` 和商品页访问日志 `ods_log_visit_di`。调度每天 02:30 开始，等上游全部成功后才运行，处理前一天（T-1）的数据。

## 质量检查

`dwd_traffic_visit_di` 发布前要过三项检查：主键 / 粒度唯一，保证 `visit_id` 不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

这几项挂了会直接影响下游。`visit_id` 重复会让访问量和 UV 虚高，转化率被顶偏；品类字段为空会让按品类看流量的报表丢分组；行数骤降往往是 `ods_log_visit_di` 当天没抽全，UV 偏小会把转化率算得虚高。看到告警先把上游确认清楚再发布。
