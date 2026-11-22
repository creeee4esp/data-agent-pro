# 取数需求单 REQ-2025-0258

## 需求

商品部 2025-11-03 提的，想要 2025-03 的支付金额（`pay_amt`），按收货省份粒度、日度支付口径拉，只看河南省。单号 REQ-2025-0258，商品数据组接的。

## 结论

`dws_trade_prov_pay_1d`（DWS-收货省份粒度支付汇总（日））这张表能满足，字段用 `stat_date`、`receiver_province`、`pay_cnt`、`pay_amt`，日期 `stat_date` 取 2025-03，并按 `receiver_province` 只留河南省。

加和这块省心：`pay_cnt` 和 `pay_amt` 都可跨行相加，把河南省当月的 `pay_amt` 逐日累加算月度支付金额没问题，`pay_cnt` 同理可汇总出支付笔数。

这次已交付，并沉淀为固定报表。
