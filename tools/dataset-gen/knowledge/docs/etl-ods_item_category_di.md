# 加工说明：ods_item_category_di

## 加工逻辑

`ods_item_category_di` 存的是 ODS-商品品类表（两级），当前 22 行。它是业务源表 `item_category` 在贴源层的落地，加工上没什么花样：整表原样搬过来，不清洗也不聚合，只在源表全部字段后面追加一个固定的 ETL 时间列和分区列 `dt`，用来记录抽取时间、支持按日分区。

```sql
INSERT INTO ods_item_category_di SELECT item_category.*, <固定 etl_time>, <分区 dt> FROM item_category
```

跟新同事说一句：这张表里同时放着一级和两级品类，下游 `dim_sku` 和订单明细宽表是靠 `parent_id` 自关联来拼出一级、二级层级的，所以这里的 `parent_id` 一旦缺失或错位，下游的品类层级就会跟着错。

## 依赖与调度

`ods_item_category_di` 依赖一张上游表，业务源表 `item_category`。调度每天 01:00 开始，等上游全部成功后才运行，处理前一天（T-1）的数据。作为 ODS 层任务，它跑在 DWD、DIM、ADS 各层前面。

## 质量检查

`ods_item_category_di` 发布前要过三项检查：主键 / 粒度唯一，保证品类不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

这几项挂了会往下游传。品类重复会让 `dim_sku` 按品类关联时膨胀，销售按品类汇总跟着翻倍；`parent_id` 等关键字段为空会让两级品类拼不起来，一级品类落空；行数骤降往往是源表 `item_category` 没抽全，部分品类在下游关联不上。看到告警先把源表确认清楚再发布。
