# 加工说明：ods_mkt_coupon_receive_di

## 加工逻辑

`ods_mkt_coupon_receive_di` 存的是 ODS-用户领券记录表，当前 6,739 行，是业务源表 `mkt_coupon_receive` 的贴源落地。加工整表原样同步，不清洗不聚合，只在源表全部字段后追加固定 ETL 时间列和分区列 `dt`。

```sql
INSERT INTO ods_mkt_coupon_receive_di SELECT mkt_coupon_receive.*, <固定 etl_time>, <分区 dt> FROM mkt_coupon_receive
```

举个取数例子：要看券的核销情况，直接用这张表——下游 `ads_coupon_usage_monthly` 就是把它和 `dim_coupon` 关联后，按领券月份和券类型统计领券张数、已核销张数和核销率的。这里的 `use_status` 等于 `used` 代表已核销，算核销率时以此为准。

## 依赖与调度

`ods_mkt_coupon_receive_di` 依赖一张上游表，业务源表 `mkt_coupon_receive`。调度每日 01:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。作为 ODS 层任务，它跑在下游各层前面。

## 质量检查

`ods_mkt_coupon_receive_di` 发布前跑三项检查：主键 / 粒度唯一，保证领券记录不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查失败会往下游传。领券记录重复会让领券张数和核销张数被重复累加，核销率失真；关键字段为空会让按券汇总的下游缺失；行数骤降往往是源表 `mkt_coupon_receive` 没抽全，领券规模被低估。看到告警先核查源表再发布。
