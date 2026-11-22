# 指标说明：退款笔数（refund_cnt）

## 计算公式

退款笔数的口径是 `COUNT(*)`，对退款明细记录逐行计数。

- 聚合方式：逐行计数，不去重。
- 时间归属：按退款日期 `refund_date` 归属。
- 可加和：可以。各天、各周结果可直接相加，日粒度求和即得更长周期。

## 取哪张表

按周期选后缀：日用 `_1d`，周用 `_1w`，月用 `_1m`。

- 省份维度：`dws_trade_prov_refund_1d` / `dws_trade_prov_refund_1w` / `dws_trade_prov_refund_1m`。
- 退款原因维度：`dws_trade_reason_refund_1d` / `dws_trade_reason_refund_1w` / `dws_trade_reason_refund_1m`。
- 用户维度：`dws_trade_user_refund_1d` / `dws_trade_user_refund_1w` / `dws_trade_user_refund_1m`。

选一个维度即可，跨维度不要相加。

## 来源与历史变化

退款笔数来自退款明细表 `dwd_trade_refund_di`，上述各汇总表均由它按维度聚合而来。

- 历史变更：自上线以来未调整计算规则。
- 口径一致，可直接用于跨期趋势比较。
