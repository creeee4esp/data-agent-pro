# 取数需求单 REQ-2025-0079

## 需求

物流部要一份 2025-02 至 2025-04、按收货省份看每月下单数 `order_cnt` 的数据，没有筛选条件，主要想结合各省成交量来排布后续的仓配资源。

## 结论

`dws_trade_prov_order_1m` 是收货省份粒度的按月下单汇总表，正好对口。取 `stat_date`、`receiver_province`、`order_cnt`、`item_qty`、`paid_amount`，按 `stat_date` 卡在 2025-02 到 2025-04 这三个月。

要提醒物流部的是，下单数 `order_cnt` 是不能跨行相加的。想看某个省这三个月的合计，不能把各月 `order_cnt` 直接加起来，会重复计数；要排省份的量级，建议用可加的 `item_qty` 或 `paid_amount`，或者就按月逐行对比。

这次已经按一次性取数交付物流部了。
