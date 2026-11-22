# 加工说明：dws_traffic_sku_visit

## 加工逻辑

`dws_traffic_sku_visit` 系列把访问明细按商品 SKU 汇总，从 `dwd_traffic_visit_di` 读数，产出日、周、月三张表。日表按 `visit_date`、`sku_id` 分组，算访问次数 `COUNT(*)`、访客数 `COUNT(DISTINCT visitor_id)` 和停留时长 `SUM(stay_seconds)`：

```sql
INSERT INTO dws_traffic_sku_visit_1d
SELECT visit_date, sku_id, COUNT(*), COUNT(DISTINCT visitor_id), SUM(stay_seconds)
FROM dwd_traffic_visit_di
GROUP BY visit_date, sku_id
```

取数例子：查某 SKU 某天访问次数，`SELECT * FROM dws_traffic_sku_visit_1d WHERE visit_date = '2025-10-25' AND sku_id = 'xxx'`；看某 SKU 某月停留时长，直接查月表 `dws_traffic_sku_visit_1m WHERE visit_date = '2025-10-01'`（月表日期已归到月初）。周表 `dws_traffic_sku_visit_1w` 的日期是 `DATE_SUB(visit_date, INTERVAL WEEKDAY(visit_date) DAY)` 算出的周一。三张表当前分别约 55,584 行（日）、15,625 行（周）、3,600 行（月）。

## 依赖与调度

`dws_traffic_sku_visit` 的上游是访问明细表 `dwd_traffic_visit_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_traffic_sku_visit` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。例如访问次数合计应与 `dwd_traffic_visit_di` 当日总数一致，而访客数因去重不参与简单加和对账。检查失败会直接影响下游 SKU 流量看板，访问次数或停留时长会算错。
