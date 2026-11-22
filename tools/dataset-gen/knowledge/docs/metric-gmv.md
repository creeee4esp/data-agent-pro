# 指标说明：GMV：已支付订单明细金额之和（元）（gmv）

## 计算公式

`ads_trade_gmv_monthly` 里的 GMV 口径是对已支付明细金额求和，即 `SUM(IF(is_paid = 1, item_amount, 0))`，报表按 `DATE_FORMAT(order_date, '%Y-%m')` 聚合到自然月。最容易误用的一点是时间归属：GMV 按下单日期 `order_date` 归属，而不是支付日期，和按支付日归属的支付金额不是一回事，做对比时别混用。第二个坑是可加性：GMV 这类金额在报表里虽是按月加工，但同一张报表里它和去重类指标混放，不要把它当成能随意跨维度叠加的量，取数时以报表口径为准。

## 取哪张表

查 GMV 直接取报表最稳妥。看月度 GMV 用 `ads_trade_gmv_monthly`，该表加工时以 `DATE_FORMAT(order_date, '%Y-%m')` 分组；看日度口径用 `ads_trade_gmv_daily`。不要自己从明细临时拼口径，报表已经把已支付过滤、按月聚合都做好了。

## 来源与历史变化

GMV 的报表加工回溯到下单明细表 `dwd_trade_order_detail_di`，加工 SQL 为 `INSERT INTO ads_trade_gmv_monthly SELECT DATE_FORMAT(order_date, '%Y-%m'), SUM(IF(is_paid = 1, item_amount, 0)), COUNT(DISTINCT IF(is_paid = 1, order_id, NULL)), COUNT(DISTINCT IF(is_paid = 1, buyer_id, NULL)) FROM dwd_trade_order_detail_di GROUP BY DATE_FORMAT(order_date, '%Y-%m')`。历史变更方面，2025-01-24 由流量数据组发布说明：GMV 统计已支付过的订单（`pay_time` 不为空），含后续退款。也就是说，只要支付过就计入 GMV，后续发生退款不从 GMV 中扣减。
