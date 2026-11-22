# 取数需求单 REQ-2025-0126

## 需求

运营部希望查看 2025-05 单月、收货省份为河南省的按日已签收单数 `signed_cnt`，筛选只看河南省，目的是跟踪该省的履约签收情况，评估大促期间当地配送的交付质量。

## 结论

`dws_lgs_prov_ship_1d` 是收货省份粒度的按日发货汇总表，字段含 `stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours`，可直接满足需求。取数时以 `receiver_province` 等于河南省过滤，`stat_date` 限定在 2025-05。

从口径上分析，已签收单数 `signed_cnt` 与发货单数 `ship_cnt` 都是可跨行相加的，在已锁定河南省的前提下，把各天的 `signed_cnt` 累加成整月签收合计是成立的。需要留意平均发货时长 `avg_ship_hours` 不可相加，想看该省整月平均时效得按 `ship_cnt` 加权，不能把各天直接平均。

本次已交付，并沉淀为固定报表供运营部查看。
