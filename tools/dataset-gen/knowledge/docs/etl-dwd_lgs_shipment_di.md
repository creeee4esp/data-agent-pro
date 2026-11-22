# 加工说明：dwd_lgs_shipment_di

## 加工逻辑

`dwd_lgs_shipment_di` 存 DWD-物流明细，一条发货记录一行，当前 17,059 行。加工把物流发货单表 `ods_lgs_shipment_di` 作为主表，关联订单表 `ods_trade_order_di`（按 `order_id = ord_no`）取收货省份，关联仓库表 `ods_lgs_warehouse_di`（按 `warehouse_id`）取仓库大区。

时效字段用 `TIMESTAMPDIFF(HOUR, ...)` 按小时计算：发货时长是支付到发货，`TIMESTAMPDIFF(HOUR, o.pay_time, s.ship_time)`；送达时长是发货到签收，`TIMESTAMPDIFF(HOUR, s.ship_time, s.sign_time)`。签收标记用 `IF(s.sign_time IS NULL, 0, 1)`，未签收记 0。

```sql
SELECT s.ship_id, s.ord_no, s.warehouse_id, w.region, s.carrier, o.receiver_province,
  TIMESTAMPDIFF(HOUR, o.pay_time, s.ship_time), TIMESTAMPDIFF(HOUR, s.ship_time, s.sign_time),
  IF(s.sign_time IS NULL, 0, 1), DATE(s.ship_time), s.dt
FROM ods_lgs_shipment_di s JOIN ods_trade_order_di o ON o.order_id = s.ord_no
JOIN ods_lgs_warehouse_di w ON w.warehouse_id = s.warehouse_id
```

两个容易误用的地方要提醒。第一，连接键是 `o.order_id = s.ord_no`，发货单侧用的是 `ord_no` 而不是 `order_id`，写关联时别弄混字段名。第二，`sign_time` 为空的记录送达时长会算不出来，统计平均送达时长时要留意用签收标记为 1 的记录，否则未签收单会污染均值。

## 依赖与调度

`dwd_lgs_shipment_di` 依赖三张上游表：物流发货单表 `ods_lgs_shipment_di`、仓库表 `ods_lgs_warehouse_di` 和订单表 `ods_trade_order_di`。调度每日 02:30 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dwd_lgs_shipment_di` 发布前跑三项检查：主键 / 粒度唯一，保证 `ship_id` 不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查失败会直接影响下游。`ship_id` 重复会让发货单量被重复计算，时效均值被拉偏；大区或收货省份为空会让区域维度的时效报表丢分组；行数骤降往往是 `ods_lgs_shipment_di` 没抽全，下游会低估发货规模。看到告警先定位上游再发布。
