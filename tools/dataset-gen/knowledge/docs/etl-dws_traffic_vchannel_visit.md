# 加工说明：dws_traffic_vchannel_visit

## 加工逻辑

`dws_traffic_vchannel_visit` 系列把访问明细按访问渠道汇总，源表 `dwd_traffic_visit_di`，产出日、周、月三张表。

- 分组键：`visit_date`、`channel`。
- 指标：访问次数 `COUNT(*)`、访客数 `COUNT(DISTINCT visitor_id)`、停留时长 `SUM(stay_seconds)`。

```sql
INSERT INTO dws_traffic_vchannel_visit_1d
SELECT visit_date, channel,
       COUNT(*), COUNT(DISTINCT visitor_id), SUM(stay_seconds)
FROM dwd_traffic_visit_di
GROUP BY visit_date, channel
```

- 周表 `dws_traffic_vchannel_visit_1w`：日期 `DATE_SUB(visit_date, INTERVAL WEEKDAY(visit_date) DAY)` 归到周一。
- 月表 `dws_traffic_vchannel_visit_1m`：日期 `DATE_FORMAT(visit_date, '%Y-%m-01')` 归到月初。
- 访问次数、停留时长可加和；访客数去重，不可加和。
- 行数：日约 1,095、周约 159、月约 36。

## 依赖与调度

`dws_traffic_vchannel_visit` 上游为访问明细表 `dwd_traffic_visit_di`。

- 调度：每日 03:30 开始。
- 触发：依赖上游全部成功后运行。
- 范围：处理前一天（T-1）的数据。

## 质量检查

`dws_traffic_vchannel_visit` 执行四项检查：

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。
- 可加和指标总额与来源明细对账（DWS 必须等于 DWD），仅针对访问次数、停留时长。

检查失败影响下游渠道流量分析，访问次数或停留时长口径会错。
