# 表说明：aftersale_order（售后单表）

## 概述

`aftersale_order` 是售后单的业务明细表，属于 CORE 层，表注释为售后单表，当前 1,025 行。它直接来自业务系统的在线数据库，记录每一笔退货、换货、维修的申请与处理状态，是售后分析的源头。要先点出一处易错点：售后类型里只有退货会涉及退款，换货和维修不等于退款，别把售后单数直接当退款单数用。

## 字段

- `aftersale_id` 是 bigint 的售后单ID，是本表主键，唯一标识一笔售后。
- `order_item_id` 是 bigint 的订单明细ID，即 trade_order_item.id，用它关联回具体的订单明细行。
- `aftersale_type` 是 varchar(10) 的售后类型，取值 return 表示退货、exchange 表示换货、repair 表示维修，统计退款相关口径时只应关注 return。
- `apply_time` 是 datetime 的申请时间，晚于签收时间，表示用户发起售后的时刻。
- `status` 是 varchar(10) 的处理状态，取值 processing 表示处理中、closed 表示已完结、rejected 表示已驳回，统计有效售后时要注意排除 rejected。

## 上下游

`aftersale_order` 的数据来自业务系统（在线数据库），通过同步进入数仓。它的下游是 `ods_aftersale_order_di`，即对应的 ODS 贴源表，后续的明细和汇总加工都从 ODS 侧展开。按关联关系，`aftersale_order.order_item_id` 指向 `trade_order_item.id`，可据此把售后关联到订单明细。

## 使用注意

`aftersale_order` 适合做售后单量、售后类型分布、处理状态分析，以及通过 `order_item_id` 回溯对应订单明细。它不适合直接当退款金额或退款率的来源，退款相关统计应走退款口径的明细表；统计时也要结合 `status` 排除已驳回的单。作为 CORE 业务表，常规分析更建议从 ODS 及下游加工表取数。本家族还有备份表 `aftersale_order_bak` 和一批带日期备份如 `aftersale_order_bak_20250415`，它们是某时点的留存副本，不随业务更新、不保证完整，不能用于统计，口径一律以 `aftersale_order` 及其规范下游为准。
