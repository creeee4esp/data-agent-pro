# 加工说明：ods_lgs_shipment_di

## 加工逻辑

`ods_lgs_shipment_di` 存的是 ODS-物流发货单表，当前 17,059 行，是业务源表 `lgs_shipment` 的贴源落地。加工整表原样同步，不清洗不聚合，只在源表全部字段后追加固定 ETL 时间列和分区列 `dt`。

```sql
INSERT INTO ods_lgs_shipment_di SELECT lgs_shipment.*, <固定 etl_time>, <分区 dt> FROM lgs_shipment
```

举个取数例子：要看原始发货单，直接查这张表即可；但要算发货、送达时效，应该用下游 `dwd_lgs_shipment_di`，那里才算好了支付到发货、发货到签收的小时数，还补上了仓库大区和收货省份。

## 依赖与调度

`ods_lgs_shipment_di` 依赖一张上游表，业务源表 `lgs_shipment`。调度每日 01:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。作为 ODS 层任务，它跑在下游各层前面。

## 质量检查

`ods_lgs_shipment_di` 发布前跑三项检查：主键 / 粒度唯一，保证发货单不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查失败会往下游传。发货单重复会让 `dwd_lgs_shipment_di` 的发货单量翻倍，时效均值被拉偏；关键字段为空会让下游时效口径缺失；行数骤降往往是源表 `lgs_shipment` 没抽全，发货规模被低估。看到告警先核查源表再发布。
