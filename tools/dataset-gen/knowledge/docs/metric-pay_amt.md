# 指标说明：支付金额（元）（pay_amt）

## 计算公式

支付金额的口径是 `SUM(pay_amt)`，对支付明细行的支付金额求和。

- 聚合方式：直接累加，不去重。
- 时间归属：按支付日期 `pay_date` 归属。
- 可加和：可以。各天、各周结果可直接相加，日表求和即得月度结果。

## 取哪张表

按周期选后缀：日用 `_1d`，周用 `_1w`，月用 `_1m`。

- 渠道维度：`dws_trade_channel_pay_1d` / `dws_trade_channel_pay_1w` / `dws_trade_channel_pay_1m`。
- 省份维度：`dws_trade_prov_pay_1d` / `dws_trade_prov_pay_1w` / `dws_trade_prov_pay_1m`。
- 用户维度：`dws_trade_user_pay_1d` / `dws_trade_user_pay_1w` / `dws_trade_user_pay_1m`。
- 月度渠道报表：`ads_pay_channel_monthly`。

同一笔金额在不同维度表里都会出现，跨维度不要相加。

## 来源与历史变化

支付金额来自支付明细表 `dwd_trade_pay_di`，上游为 `ods_pay_order_di`，各汇总表与报表均由明细聚合而来。

- 历史变更：2025-05-07 由数据平台组发布说明——支付金额按支付日期归属，与按下单日期归属的 GMV 不同。
- 取数提醒：对比 GMV 时注意两者时间归属口径不一致，不能直接相减或等同。
