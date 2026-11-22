# 加工说明：ods_lgs_warehouse_di

## 加工逻辑

`ods_lgs_warehouse_di` 为 ODS-仓库表，当前 6 行，是业务源表 `lgs_warehouse` 在贴源层的落地。加工不做清洗与聚合，整表原样同步，仅在源表全部字段之外追加固定 ETL 时间列与分区列 `dt`，以记录抽取时间并支持按日分区管理。

```sql
INSERT INTO ods_lgs_warehouse_di SELECT lgs_warehouse.*, <固定 etl_time>, <分区 dt> FROM lgs_warehouse
```

本表粒度与源表一致，一座仓库对应一行，是下游 `dwd_lgs_shipment_di` 关联获取仓库大区的依据。

## 依赖与调度

`ods_lgs_warehouse_di` 依赖一张上游表，即业务源表 `lgs_warehouse`。调度每日 01:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。作为 ODS 层任务，其运行时间早于下游各层。

## 质量检查

`ods_lgs_warehouse_di` 发布前执行三项检查：主键 / 粒度唯一，确保仓库不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查未通过将影响下游。仓库重复会使 `dwd_lgs_shipment_di` 按仓库关联时膨胀，大区维度统计失真；关键字段为空会导致仓库大区缺失；行数异常下降通常表明源表 `lgs_warehouse` 未抽取完整，部分发货单关联不到仓库。出现告警应先核查源表后再行发布。
