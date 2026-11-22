# 指标说明：评价数（review_cnt）

## 计算公式

评价数的口径是 `COUNT(*)`，对评价明细逐行计数，一条评价算一条。时间归属按评价日期 `review_date`，哪天评价算到哪天。该指标可加和，各天、各周可直接相加。举例：想要某 SKU 一周的评价数，取日表后把当周 7 天的 `review_cnt` 相加即可；想要一个月的，直接取月表更省事。

## 取哪张表

取数先按周期选后缀，日用 `_1d`、周用 `_1w`、月用 `_1m`，再按维度选表。

- 品牌维度：`dws_item_brand_review_1d` / `dws_item_brand_review_1w` / `dws_item_brand_review_1m`。
- 一级品类维度：`dws_item_cate1_review_1d` / `dws_item_cate1_review_1w` / `dws_item_cate1_review_1m`。
- SKU 维度：`dws_item_sku_review_1d` / `dws_item_sku_review_1w` / `dws_item_sku_review_1m`。

举两个例子：要看某品牌昨天的评价数，查 `dws_item_brand_review_1d`；要看某一级品类上月评价数，查 `dws_item_cate1_review_1m`。若要月度评分口径汇总，用 `ads_item_review_score_monthly`。

## 来源与历史变化

评价数来自评价明细表 `dwd_item_review_di`，上面各汇总表与报表都由它按维度聚合而来。该指标自上线以来未调整计算规则，老数据和新数据口径一致，做趋势对比不用担心口径断层。
