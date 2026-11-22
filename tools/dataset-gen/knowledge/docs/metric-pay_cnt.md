# 指标说明：支付笔数（pay_cnt）

## 计算公式

`pay_cnt` 定义为一段时间内发生的支付行为笔数，计算口径为 `COUNT(*)`，即对支付明细记录逐行计数。时间归属以支付日期 `pay_date` 为准，支付动作发生在哪一天即计入哪一天。该指标具备可加性：行与行之间互不重叠，各天、各周的结果可直接相加，日粒度汇总求和即可得到更长周期的支付笔数。

## 取哪张表

统计表的选择取决于统计周期与分析维度。按周期区分，日粒度使用 `_1d` 后缀表，周粒度使用 `_1w` 后缀表，月粒度使用 `_1m` 后缀表。

- 渠道维度：日用 `dws_trade_channel_pay_1d`，周用 `dws_trade_channel_pay_1w`，月用 `dws_trade_channel_pay_1m`。
- 省份维度：日用 `dws_trade_prov_pay_1d`，周用 `dws_trade_prov_pay_1w`，月用 `dws_trade_prov_pay_1m`。
- 用户维度：日用 `dws_trade_user_pay_1d`，周用 `dws_trade_user_pay_1w`，月用 `dws_trade_user_pay_1m`。

若需按支付渠道查看月度支付笔数，另可使用月度汇总报表 `ads_pay_channel_monthly`。

## 来源与历史变化

`pay_cnt` 来源于支付明细表 `dwd_trade_pay_di`，上述各汇总表与报表均由该明细表按不同维度聚合而成。该指标自上线以来未调整计算规则，历史口径与当前口径一致，可直接用于跨期趋势比较。
