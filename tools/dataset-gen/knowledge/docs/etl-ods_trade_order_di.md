# 加工说明：ods_trade_order_di

## 加工逻辑

`ods_trade_order_di`（ODS-交易订单主表）把业务库 `trade_order` 全量搬进数仓，不做清洗聚合，只追加加工时间 `etl_time` 和分区 `dt`：

```sql
INSERT INTO ods_trade_order_di
SELECT trade_order.*, <固定 etl_time>, <分区 dt>
FROM trade_order
```

取数时直接查 `ods_trade_order_di` 即可，比如要看某天订单量，用 `SELECT COUNT(*) FROM ods_trade_order_di WHERE dt = '2025-10-25'`；要按分区看最新一天数据，加上 `WHERE dt = '当天分区'` 过滤。当前产出约 20,000 行。

## 依赖与调度

`ods_trade_order_di` 的上游是业务库订单主表 `trade_order`。每日 01:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据，写入对应 `dt` 分区。例如 10 月 26 日凌晨这一批处理的是 10 月 25 日的订单。

## 质量检查

`ods_trade_order_di` 产出后执行三项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警。举例来说，若昨天 20,000 行、今天突然掉到 10,000 行，波动已超 30% 会触发告警。检查失败会直接影响下游所有以订单主表为源的 DWD/DWS，比如按订单统计的下单汇总会出现订单缺失或重复，口径随之失真。
