# 变更公告：新表上线（dws_trade_user_refund_1m）

## 变更说明

`dws_trade_user_refund_1m` 新表上线，表注释为“DWS-买家用户粒度退款汇总（月）”，由数据平台组发布，2025-09-19 生效。

该表上游为 `dwd_trade_refund_di`，按买家用户、月粒度汇总退款情况，可用于用户维度的月度退款分析。

影响范围为需要买家用户月度退款数据的报表与建模场景。使用方自生效日起可接入 `dws_trade_user_refund_1m` 取数，注意粒度为买家用户 × 月；上游 `dwd_trade_refund_di` 异常时本表产出可能延迟，请关注依赖状态。
