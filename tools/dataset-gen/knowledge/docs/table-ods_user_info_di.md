# 表说明：ods_user_info_di（ODS-用户基础信息表）

## 概述

`ods_user_info_di` 为 ODS 层的用户基础信息贴源表，原样承接业务侧的用户主数据，当前共 2,000 行。该表以用户为粒度，一行描述一名用户，相比业务表增加了 `etl_time` 与 `dt` 两个技术字段。其作为用户维度的核心贴源数据，是用户画像、会员结构及地域分布等分析的基础，并向下游多张维度与明细表供数。

## 字段

- `user_id`（bigint）：用户 ID，作为用户的唯一标识，关联时以该字段为准。
- `user_name`（varchar(50)）：用户姓名，属个人信息，统计分析通常不直接使用。
- `gender`（char(1)）：性别，取值为 M-男、F-女、U-未知，按性别统计时须考虑未知值。
- `birth_year`（smallint）：出生年份，为年份值，计算年龄时须以当前年份相减。
- `province`（varchar(20)）：常住省份，如 浙江省、上海市，按常住地做地域分析时以该字段汇总。
- `city`（varchar(20)）：常住城市，可用于更细的城市级分析。
- `member_level`（tinyint）：会员等级，取值为 1-普通、2-白银、3-黄金、4-铂金、5-钻石，按会员层级统计时应使用对应枚举值。
- `register_time`（datetime）：注册时间，按注册日期做新增用户统计时以该字段为准。
- `etl_time`（datetime）：ETL 同步时间，为技术字段，不代表业务发生时间，不应用于业务口径统计。
- `dt`（date）：分区日期，用于分区裁剪。

## 上下游

`ods_user_info_di` 的数据来自上游的 `user_info`，经 ETL 同步进入 ODS 层。其下游包括 `dim_region`、`dim_user`、`dim_user_zip` 及明细层的 `dwd_trade_order_detail_di`，由上述各表承接用户属性并继续加工。

## 使用注意

`ods_user_info_di` 适合用于用户画像、会员等级结构及常住地域分布等分析，查询时建议带 `dt` 做分区过滤。常住地 `province`、`city` 描述的是用户归属地，与订单的收货省份并非同一概念，做地域分析时须明确所用口径。该表不含交易或访问行为，相关分析需关联对应链路的表。库内存在若干同家族噪声表：备份表 `ods_user_info_di_bak`、带日期备份 `ods_user_info_di_bak_20250128`、`ods_user_info_di_bak_20250613` 以及测试表 `test_ods_user_info_di`。上述表分别为历史快照或调试用途，其口径与时效不受保证，不得用于正式统计。正式取数一律以 `ods_user_info_di` 为准。
