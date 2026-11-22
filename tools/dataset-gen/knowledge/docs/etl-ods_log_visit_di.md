# 加工说明：ods_log_visit_di

## 加工逻辑

`ods_log_visit_di` 存的是 ODS-商品页访问日志，当前 95,138 行，是这批 ODS 表里体量最大的一张，来自业务源表 `log_visit` 的贴源落地。加工整表原样同步，不清洗不聚合，只在源表全部字段后追加固定 ETL 时间列和分区列 `dt`。

```sql
INSERT INTO ods_log_visit_di SELECT log_visit.*, <固定 etl_time>, <分区 dt> FROM log_visit
```

举个取数例子：要看原始访问日志，直接查这张表；但要按品牌、一级品类看流量，应该用下游 `dwd_traffic_visit_di`，那里才关联 `dim_sku` 补上了品牌和品类。算 UV 时记得对访客去重，别直接数行数。

## 依赖与调度

`ods_log_visit_di` 依赖一张上游表，业务源表 `log_visit`。调度每日 01:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。作为 ODS 层任务，它跑在下游各层前面。

## 质量检查

`ods_log_visit_di` 发布前跑三项检查：主键 / 粒度唯一，保证访问记录不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查失败会往下游传。记录重复会让 `dwd_traffic_visit_di` 的访问量和 UV 虚高，转化率被顶偏；关键字段为空会让下游流量口径缺失；行数骤降往往是源表 `log_visit` 没抽全，UV 偏小会把转化率算得虚高。看到告警先核查源表再发布。
