# 取数需求单 REQ-2025-0300

## 需求

商品部于 2025-12-30 提出，想要 `refund_amount`（退款金额（元）），按买家用户粒度的退款月汇总来看，时间范围是 2025-06，没有额外筛选。

## 结论

`dws_trade_user_refund_1m` 这张表能满足需求，交易数据组用它出数。表里有 `stat_date`、`buyer_id`、`refund_cnt`、`refund_amount` 这几个字段。

口径上比较简单：`refund_cnt` 和 `refund_amount` 都可以跨行相加，跨用户汇总当月总退款也没问题，直接聚合即可。

结果已经作为一次性取数交付给商品部了。
