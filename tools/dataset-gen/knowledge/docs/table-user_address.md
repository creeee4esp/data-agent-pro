# 表说明：user_address（用户收货地址表）

## 概述

`user_address` 是 CORE 层的用户收货地址表，记录用户维护的收货地址。

- 粒度：一条收货地址一行，一名用户可有多条。
- 当前规模：4,017 行。
- 适用场景：收货地域分布、默认地址与地址标签分析。

## 字段

- `address_id`（bigint）：收货地址 ID，单条地址的唯一标识。
- `user_id`（bigint）：用户 ID，按用户聚合地址用它。
- `receiver_name`（varchar(50)）：收货人姓名，属个人信息，统计一般不直接用。
- `province`（varchar(20)）：省份，做地域分布按它汇总。
- `city`（varchar(20)）：城市，做城市级分析用它。
- `address_tag`（varchar(10)）：地址标签，home-家、company-公司、other-其他，按标签分类用这些枚举值。
- `is_default`（tinyint）：是否默认地址，1-是、0-否，取默认地址用它过滤。
- `created_time`（datetime）：创建时间，按地址创建时间统计用它。

## 上下游

`user_address` 的上游是业务系统（在线数据库）。

- 下游：ODS 层的 `ods_user_address_di`，由它承接再往上层加工。
- 关联：`user_address.user_id` 指向 `user_info.user_id`，据此带出用户基础信息。

## 使用注意

`user_address` 适合做收货地域分布、默认地址占比和地址标签分类。

- 注意：一名用户有多条地址，按用户统计要先明确是否只取默认地址，否则会重复计数。
- 不适合：承载订单或交易信息，那些在交易链路。
- 噪声表：备份表 `user_address_bak`、带日期备份如 `user_address_bak_20250110`、旧版本 `user_address_v1`。
- 为什么不能用：历史快照或早期结构，口径与时效都不保证。
- 正式取数只认 `user_address`。
