# 表说明：ads_lgs_delivery_monthly（ADS-仓库月度物流时效）

## 概述

`ads_lgs_delivery_monthly` 是按仓库大区统计的月度物流时效表，位于 ADS 层，表注释为 ADS-仓库月度物流时效，当前 77 行。它由物流明细表 `dwd_lgs_shipment_di` 按发货月和仓库大区聚合得到，想看哪个大区发货快、配送慢，看它就行。有一点要先跟新同事交代清楚：月份是按发货月算的，配送时长还只统计已签收的单，没签收的不进平均值。

## 字段

- `stat_month` 是 char(7) 的统计月份，按发货月归属，也就是以发货动作所在的月份划分，不是下单月也不是签收月。
- `warehouse_region` 是 varchar(10) 的仓库大区，是本表除月份外的拆分维度，粒度到大区而非单个仓库。
- `ship_cnt` 是 int 的发货单数，表示当月该大区发出的单量。
- `avg_ship_hours` 是 decimal(10,2) 的平均发货时长，单位小时，衡量从支付到发货的速度。
- `avg_deliver_hours` 是 decimal(10,2) 的平均配送时长，单位小时，且只统计已签收的单，未签收的单不参与计算，所以读它时要意识到这是已完成配送的样本。

## 上下游

`ads_lgs_delivery_monthly` 的数据全部来自 `dwd_lgs_shipment_di`，加工时按发货日期的月份和仓库大区分组，汇总发货单数并计算两个平均时长。它没有下游表，是直接服务报表和分析的末端结果。

## 使用注意

`ads_lgs_delivery_monthly` 适合做大区级别的月度时效对比，比如比较各大区的发货和配送速度、看单量分布。它不适合下钻到单个仓库、承运商或具体省份，那些需求要回到更细的 DWS 表或明细表。用 `avg_deliver_hours` 时还要记得它只覆盖已签收的单，不能当作全部发货单的配送时长。另外本家族有备份表 `ads_lgs_delivery_monthly_bak` 和一批带日期的备份如 `ads_lgs_delivery_monthly_bak_20250217`，它们是某个时点的留存副本，不随任务更新、也不保证完整，做统计时一律用 `ads_lgs_delivery_monthly`，不要碰这些备份。
