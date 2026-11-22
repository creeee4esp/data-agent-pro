# 加工说明：ods_trade_order_item_di

## 加工逻辑

`ods_trade_order_item_di`（ODS-交易订单明细表）承接业务库 `trade_order_item` 的原始数据，作为贴源层不实施任何过滤、去重或聚合操作，仅将源表全部字段原样抽取入库，并补充加工时间 `etl_time` 与分区标识 `dt`。加工语句如下：

```sql
INSERT INTO ods_trade_order_item_di
SELECT trade_order_item.*, <固定 etl_time>, <分区 dt>
FROM trade_order_item
```

其中 `etl_time` 记录本次抽取的加工时刻，`dt` 用于按日分区存储。当前产出约 35,138 行。

## 依赖与调度

`ods_trade_order_item_di` 的唯一上游为业务库订单明细表 `trade_order_item`。调度策略为每日 01:00 开始，在依赖上游全部成功后运行，处理前一天（T-1）的数据，并写入对应 `dt` 分区。

## 质量检查

`ods_trade_order_item_di` 入库后须依次通过三项质量检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警。上述任一检查未通过，将直接影响以订单明细为数据来源的下游 DWD 与 DWS 加工，导致订单明细口径出现缺失、重复或粒度错误，进而污染依赖该明细的各类汇总结果。
