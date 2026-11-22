# 表说明：dim_user（DIM-用户维表）

## 概述

`dim_user` 是用户维表，属于 DIM 层，表注释为 DIM-用户维表，当前 2,000 行，一个用户一行。它由 `ods_user_address_di` 和 `ods_user_info_di` 加工，提供用户的基础属性、地域、会员等级和注册信息。想给带用户ID的事实补充性别、年龄段、地区或会员等级，关联它即可，比如 `SELECT age_group, COUNT(*) FROM dim_user GROUP BY age_group`。

## 字段

- `user_id` 是 bigint 的用户ID，是本表主键和关联键。
- `user_name` 是 varchar(50) 的用户姓名，用于展示，不建议用于关联。
- `gender` 是 char(1) 的性别。
- `age_group` 是 varchar(10) 的年龄段，由出生年份分档得到，如 00后、90后、80后、80前，缺失时为未知。
- `province` 是 varchar(20) 的常住省份，注意是用户常住地，不是订单收货省份。
- `region` 是 varchar(10) 的大区，由常住省份映射而来。
- `member_level` 是 tinyint 的会员等级，取当前等级，分析历史等级要用拉链表而非本表。
- `register_date` 是 date 的注册日期。
- `address_cnt` 是 int 的收货地址数，表示该用户名下地址条数。

## 上下游

`dim_user` 的上游是 `ods_user_info_di`（用户基础信息，含注册时间、常住省份、会员等级）和 `ods_user_address_di`（用于统计地址数）。加工时以用户为主，分档年龄、映射大区并统计地址数。它当前没有下游表，供分析按需关联。

## 使用注意

`dim_user` 适合做用户画像类的关联和查询，如按 `age_group`、`gender`、`region`、`member_level` 分布统计，或 `SELECT region, COUNT(*) FROM dim_user GROUP BY region`。它的 `member_level` 是当前快照，要看会员等级的历史变化应改用拉链表；`province` 是常住地，别和收货省份混用。它是维表不含交易度量，不能直接统计消费。本家族有历史归档表 `dim_user_his`、备份表 `dim_user_bak`、带日期备份 `dim_user_bak_20250123` 和测试表 `test_dim_user`，都是旧快照、时点副本或调试产物，统计与关联一律用 `dim_user`。
