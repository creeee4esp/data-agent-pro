# 加工说明：ods_item_brand_di

## 加工逻辑

`ods_item_brand_di` 为 ODS-商品品牌表，当前 12 行，是业务源表 `item_brand` 的贴源落地。

要点：

- 整表原样同步，不做清洗、不做聚合。
- 在源表全部字段后追加固定 ETL 时间列与分区列 `dt`。
- 粒度与源表一致，一个品牌一行。

```sql
INSERT INTO ods_item_brand_di SELECT item_brand.*, <固定 etl_time>, <分区 dt> FROM item_brand
```

## 依赖与调度

`ods_item_brand_di` 依赖一张上游表。

- 上游表：业务源表 `item_brand`。
- 调度：每日 01:00 开始。
- 触发：依赖上游全部成功后运行。
- 口径：处理前一天（T-1）的数据。

## 质量检查

`ods_item_brand_di` 发布前跑三项检查。

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。

失败影响：

- 品牌重复，下游 `dim_sku` 按品牌关联会膨胀。
- 关键字段为空，品牌属性在下游缺失。
- 行数骤降，说明源表 `item_brand` 没抽全，部分品牌在下游关联不上。
