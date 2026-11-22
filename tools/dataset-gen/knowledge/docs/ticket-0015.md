# 取数需求单 REQ-2025-0015

## 需求

REQ-2025-0015，财务部于 2025-01-16 提出。

- 需求方：财务部
- 诉求：收货省份粒度的已签收单数 `signed_cnt`
- 口径：按日汇总
- 时间范围：2025-01
- 筛选：只看河北省

## 结论

`dws_lgs_prov_ship_1d` 为交付用表，由数据平台组处理。

- 维度：`stat_date`、`receiver_province`
- 筛选：`receiver_province` 限定为河北省
- 输出指标：`signed_cnt` 已签收单数
- 可加性提示：`ship_cnt`、`signed_cnt` 可跨行相加，按日汇总可直接 SUM；`avg_ship_hours` 不可跨行相加，需加权计算
- 交付方式：已交付，附 SQL 供需求方自助复用
