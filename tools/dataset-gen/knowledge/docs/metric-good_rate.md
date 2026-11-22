# 指标说明：好评率（good_rate）

## 计算公式

`ads_item_review_score_monthly` 里的好评率是一个比率，分子是好评数、分母是评价总数，口径为 `ROUND(SUM(is_good) / COUNT(*), 4)`。拆开看：`is_good` 是每条评价的好评标记，4 星及以上记 1，把它累加得到好评数，再除以当期评价条数 `COUNT(*)`，保留四位小数。报表按 `DATE_FORMAT(review_date, '%Y-%m')` 归到评价月、并按一级品类 `category1_name` 分组。要提醒的是，比率是不可加和的，想算更长周期的好评率，得把分子的好评数和分母的评价数各自汇总后再相除，不能对各月好评率取平均。

## 取哪张表

查好评率就用月度评分报表 `ads_item_review_score_monthly`。它按评价月和一级品类给出好评率，直接取对应月份、对应品类那一行即可，不需要自己从明细临时拼比率。

## 来源与历史变化

好评率的报表加工一路回溯到评价明细表 `dwd_item_review_di`，加工 SQL 为 `INSERT INTO ads_item_review_score_monthly SELECT DATE_FORMAT(review_date, '%Y-%m'), category1_name, COUNT(*), ROUND(AVG(rating), 2), ROUND(SUM(is_good) / COUNT(*), 4) FROM dwd_item_review_di GROUP BY DATE_FORMAT(review_date, '%Y-%m'), category1_name`。这个指标自上线以来未调整计算规则，口径一直没变，历史数据和当前数据可以放心做趋势对比。
