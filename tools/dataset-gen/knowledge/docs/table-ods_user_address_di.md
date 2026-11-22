# 表说明：ods_user_address_di（ODS-用户收货地址表）

## 概述

`ods_user_address_di` 为 ODS 层的用户收货地址贴源表，原样承接业务侧的收货地址记录，当前共 4,017 行。该表以单条收货地址为粒度，一名用户可对应多条地址，相比业务表增加了 `etl_time` 与 `dt` 两个技术字段。其作为用户维度的贴源数据，主要用于刻画用户的收货地域分布及默认地址情况，并向下游用户维度供数。

## 字段

- `address_id`（bigint）：收货地址 ID，单条地址的唯一标识。
- `user_id`（bigint）：用户 ID，用于关联用户，按用户聚合地址时以该字段为准。
- `receiver_name`（varchar(50)）：收货人姓名，属个人信息，统计分析通常不直接使用。
- `province`（varchar(20)）：省份，按省份做地域分布时以该字段汇总。
- `city`（varchar(20)）：城市，可用于更细的城市级地域分析。
- `address_tag`（varchar(10)）：地址标签，取值为 home-家、company-公司、other-其他，按标签分类时应使用对应枚举值。
- `is_default`（tinyint）：是否默认地址，1 表示是、0 表示否，取用户默认地址时应以该字段过滤。
- `created_time`（datetime）：创建时间，可用于按地址创建时间做统计。
- `etl_time`（datetime）：ETL 同步时间，为技术字段，不代表业务发生时间，不应用于业务口径统计。
- `dt`（date）：分区日期，用于分区裁剪。

## 上下游

`ods_user_address_di` 的数据来自上游的 `user_address`，经 ETL 同步进入 ODS 层。其下游为 `dim_user`，由后者承接并将地址信息加工进用户维度。

## 使用注意

`ods_user_address_di` 适合用于用户收货地域分布、默认地址占比及地址标签分类等分析，查询时建议带 `dt` 做分区过滤。由于一名用户可拥有多条地址，按用户统计时须明确是否仅取默认地址，以免重复计数。该表不含订单或交易信息，相关分析需关联交易链路的表。库内存在若干同家族噪声表：备份表 `ods_user_address_di_bak`、带日期备份 `ods_user_address_di_bak_20250116`、`ods_user_address_di_bak_20251102` 以及测试表 `test_ods_user_address_di`。上述表分别为历史快照或调试用途，其口径与时效不受保证，不得用于正式统计。正式取数一律以 `ods_user_address_di` 为准。
