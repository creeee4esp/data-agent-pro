# 加工说明：dim_sku

## 加工逻辑

`dim_sku` 为 DIM-商品维表（含品牌、两级品类），当前 300 行，粒度为 SKU，主键是 `sku_id`。加工以商品SKU表 `ods_item_sku_di` 为主表，依次左连接三张上游表以补齐品牌与品类属性。

品牌属性经 `ods_item_brand_di` 按 `brand_name` 关联，取得 `brand_id` 与 `brand_level`。两级品类通过 `ods_item_category_di` 关联两次实现：第一次以 SKU 的 `category_id` 命中二级品类，第二次再以二级品类的 `parent_id` 回连得到一级品类，从而在一行中同时输出一级与二级品类的编码和名称。

```sql
SELECT s.sku_id, s.sku_name, s.price, s.sku_status, b.brand_id, s.brand_name, b.brand_level,
  c2.category_id, c2.category_name, c1.category_id, c1.category_name
FROM ods_item_sku_di s LEFT JOIN ods_item_brand_di b ON b.brand_name = s.brand_name
LEFT JOIN ods_item_category_di c2 ON c2.category_id = s.category_id
LEFT JOIN ods_item_category_di c1 ON c1.category_id = c2.parent_id
```

品牌关联以 `brand_name` 为连接键，品类则依赖 `parent_id` 自关联构建层级，二者为本表品牌与品类口径的基础。

## 依赖与调度

`dim_sku` 依赖三张上游表：商品品牌表 `ods_item_brand_di`、商品品类表 `ods_item_category_di` 与商品SKU表 `ods_item_sku_di`。调度每日 02:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dim_sku` 发布前执行三项检查：主键 / 粒度唯一，确保 `sku_id` 不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查未通过将影响下游。`sku_id` 重复会使以商品关联的明细表出现一对多膨胀，销量与金额被重复计算；品牌或品类字段为空会导致按品类、品牌汇总的报表丢失分组或归入空值；行数异常下降通常表明上游 SKU 未抽取完整，部分商品在下游无法关联到维度属性。出现告警应先核查上游后再行发布。
