# 取数需求单 REQ-2025-0273

## 需求

客服部在 2025-11-20 提出取数，关注 2025-11 的好评数（`good_cnt`），希望按一级品类粒度、月度口径了解各品类当月的好评规模，不设筛选条件。本单编号 REQ-2025-0273，处理人数据平台组。客服部想据此看看哪些品类的好评积累较多。

## 结论

`dws_item_cate1_review_1m` 即 DWS-一级品类粒度评价汇总（月）对应本次取数，取 `stat_date`、`category1_id`、`category1_name`、`review_cnt`、`good_cnt`、`avg_rating`，报告期 `stat_date` 取 2025-11。

加和方面：`good_cnt` 与 `review_cnt` 可跨行相加，把各品类的 `good_cnt` 汇总得到全站好评数，或按品类排序找出好评集中的品类，口径准确。需要提醒 `avg_rating` 为平均值，不可跨行相加，如需整体平均评分应以 `review_cnt` 加权。

本次已交付一次性取数结果。
