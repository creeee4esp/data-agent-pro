# 加工说明：dws_traffic_cate1_visit

## 加工逻辑

`dws_traffic_cate1_visit` 系列把访问明细按一级品类汇总，从 `dwd_traffic_visit_di` 读数，产出日、周、月三张表。日表按 `visit_date`、`category1_id`、`category1_name` 分组，统计访问次数 `COUNT(*)`、访客数 `COUNT(DISTINCT visitor_id)` 和停留时长 `SUM(stay_seconds)`：

```sql
INSERT INTO dws_traffic_cate1_visit_1d
SELECT visit_date, category1_id, category1_name,
       COUNT(*), COUNT(DISTINCT visitor_id), SUM(stay_seconds)
FROM dwd_traffic_visit_di
GROUP BY visit_date, category1_id, category1_name
```

周表 `dws_traffic_cate1_visit_1w` 用 `DATE_SUB(visit_date, INTERVAL WEEKDAY(visit_date) DAY)` 把日期归到周一，月表 `dws_traffic_cate1_visit_1m` 用 `DATE_FORMAT(visit_date, '%Y-%m-01')` 归到月初。

有一处容易误用：访客数 `COUNT(DISTINCT visitor_id)` 不可加和，一个访客跨天或跨品类都会被各自去重，想算周/月的品类去重访客必须查对应周期的汇总表，不能把日表的访客数相加。访问次数 `COUNT(*)` 和停留时长 `SUM(stay_seconds)` 才是可加和的。三张表当前分别约 2,555 行（日）、371 行（周）、84 行（月）。

## 依赖与调度

`dws_traffic_cate1_visit` 的上游是访问明细表 `dwd_traffic_visit_di`。每日 03:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dws_traffic_cate1_visit` 产出后执行四项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警，以及可加和指标总额与来源明细对账（DWS 必须等于 DWD）。对账只针对访问次数、停留时长等可加和指标，去重访客数不参与。检查失败会导致下游一级品类流量分析的访问次数或停留时长失真。
