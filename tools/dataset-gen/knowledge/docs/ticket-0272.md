# 取数需求单 REQ-2025-0272

## 需求

商品部于 2025-11-17 提出取数，要点如下：

- 指标：购买件数（`item_qty`）
- 口径：收货省份粒度下单日汇总
- 时间范围：2025-06 至 2025-07
- 筛选条件：只看安徽省
- 单据编号：REQ-2025-0272，处理人流量数据组

## 结论

`dws_trade_prov_order_1d`（DWS-收货省份粒度下单汇总（日））为本次用表，要点如下：

- 取用字段：`stat_date`、`receiver_province`、`order_cnt`、`item_qty`、`paid_amount`
- 报告期：`stat_date` 覆盖 2025-06 至 2025-07，按 `receiver_province` 只留安徽省
- 加和说明：`item_qty`、`paid_amount` 可跨行相加，逐日累加可得区间件数与金额合计
- 关键提醒：`order_cnt` 不可跨行相加，不要逐日求和
- 交付情况：已交付，并沉淀为固定报表
