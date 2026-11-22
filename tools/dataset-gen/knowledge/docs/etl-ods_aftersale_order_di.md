# 加工说明：ods_aftersale_order_di

## 加工逻辑

`ods_aftersale_order_di` 为 ODS-售后单表，当前 1,025 行，是业务源表 `aftersale_order` 在数据仓库贴源层的落地。加工不做清洗与聚合，整表原样同步，仅在源表全部字段之外追加一个固定的 ETL 时间列与分区列 `dt`，以记录抽取时间并支持按日分区管理。

```sql
INSERT INTO ods_aftersale_order_di SELECT aftersale_order.*, <固定 etl_time>, <分区 dt> FROM aftersale_order
```

本表保持与源表一致的粒度，一条售后单对应一行，不改变字段语义，是售后相关下游加工的原始依据。

## 依赖与调度

`ods_aftersale_order_di` 依赖一张上游表，即业务源表 `aftersale_order`。调度每日 01:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。作为 ODS 层任务，其运行时间早于下游 DWD、DIM、ADS 各层。

## 质量检查

`ods_aftersale_order_di` 发布前执行三项检查：主键 / 粒度唯一，确保售后单不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查未通过将影响下游。售后单重复会使下游售后统计被重复计算；关键字段为空会导致相关维度缺失；行数异常下降通常表明源表 `aftersale_order` 未抽取完整，下游售后口径随之失真。作为贴源层，一旦数据异常会沿加工链向下传导，出现告警应先核查源表后再行发布。
