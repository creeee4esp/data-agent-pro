# 取数需求单 REQ-2025-0178

## 需求

商品部于 2025-07-30 正式提出取数申请，需求对象为收货省份粒度的已签收单数 `signed_cnt`，按月汇总，报告期区间为 2025-01 至 2025-02，并要求只看浙江省。该数据拟用于评估年初浙江省的签收完成情况。

## 结论

`dws_lgs_prov_ship_1m` 为本需求推荐用表，取数涉及 `stat_date`、`receiver_province`、`ship_cnt`、`signed_cnt`、`avg_ship_hours` 等字段，以 `stat_date` 限定 2025-01 至 2025-02、并在 `receiver_province` 上过滤浙江省后输出即可。需提请注意：`signed_cnt` 与 `ship_cnt` 为可加和指标，跨月求和成立；而 `avg_ship_hours` 系平均发货时长，不得跨行直接相加，整体平均须按发货单数加权重算。本需求已交付一次性取数结果。
