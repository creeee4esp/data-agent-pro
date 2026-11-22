# 表说明：item_category（商品品类表（两级））

## 概述

`item_category` 是 CORE 层的商品品类维表，用两级结构描述公司的品类体系，当前只有 22 行。它把一级品类和二级品类放在同一张表里，用 `parent_id` 把二级指回一级，所以一张表就能还原整棵两级品类树。数据量很小、改动也少，平时主要当维表用来给商品打品类标签。

## 字段

- `category_id`（int）：品类 ID，是品类的唯一标识，关联时都以它为准。
- `category_name`（varchar(50)）：品类名称，展示和分组时用它，注意同名但层级不同的情况要结合 `cate_level` 区分。
- `parent_id`（int）：父品类 ID，一级品类这里为空，二级品类指向它所属的一级 `category_id`，想做层级汇总就靠它自关联。
- `cate_level`（tinyint）：品类层级，1 表示一级品类、2 表示二级品类，只取某一层时用它过滤。

## 上下游

`item_category` 的数据来自业务系统（在线数据库），同步后进入数据平台。它的下游是 ODS 层的 `ods_item_category_di`，由后者承接再往上层加工。表内还有两条关联关系：`item_category.parent_id` 指向 `item_category.category_id`（自关联成树），以及 `item_sku.category_id` 指向 `item_category.category_id`（商品挂到二级品类上）。

## 使用注意

`item_category` 适合做品类命名、层级归属的查询，以及把商品按品类汇总时作为维度关联。它不适合承载交易量或访问量，那些要去对应的事实表。库里同家族还散着一些噪声表：备份表 `item_category_bak`，以及带日期备份如 `item_category_bak_20250416`、`item_category_bak_20250905`。它们是某次操作前留下的历史快照，口径和时效都不保证，不能拿来跑统计，正式取数只认 `item_category`。
