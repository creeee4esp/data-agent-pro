# 指标说明：退款金额（元）（refund_amount）

## 计算公式

`refund_amount` 定义为一段时间内退款金额的总和，计算口径为 `SUM(refund_amount)`，对退款明细行的退款金额逐行累加。时间归属以退款日期 `refund_date` 为准。该指标具备可加性，各行、各天、各周的结果可直接相加，日粒度汇总求和即可得到更长周期的退款金额。

## 取哪张表

统计表的选择取决于统计周期与分析维度。按周期区分，日粒度使用 `_1d` 后缀表，周粒度使用 `_1w` 后缀表，月粒度使用 `_1m` 后缀表。

- 省份维度：日用 `dws_trade_prov_refund_1d`，周用 `dws_trade_prov_refund_1w`，月用 `dws_trade_prov_refund_1m`。
- 退款原因维度：日用 `dws_trade_reason_refund_1d`，周用 `dws_trade_reason_refund_1w`，月用 `dws_trade_reason_refund_1m`。
- 用户维度：日用 `dws_trade_user_refund_1d`，周用 `dws_trade_user_refund_1w`，月用 `dws_trade_user_refund_1m`。

同一笔退款金额在不同维度汇总表中均有体现，跨维度汇总之间不得相加，以免重复计算。

## 来源与历史变化

`refund_amount` 来源于退款明细表 `dwd_trade_refund_di`，其上游链路为 `ods_pay_refund_di` 与业务侧 `pay_refund`，上述各汇总表均由退款明细按维度聚合而成。历史变更方面，2025-08-02 由数据平台组发布说明：退款金额按退款完成日期归属。进行时间维度分析时须以退款完成日期为归属口径。
