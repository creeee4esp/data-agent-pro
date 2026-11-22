# 表说明：mkt_coupon（优惠券定义表）

## 概述

`mkt_coupon` 是 CORE 层的优惠券定义表，记录每张优惠券的规则，当前 30 行，一行一种券。要知道某张券是满减还是折扣、门槛多少、面额多少、什么时候有效，查它就够了。比如列出所有无门槛券，`WHERE coupon_type = 'no_threshold'`；想找当前有效的券，用 `WHERE valid_from <= CURDATE() AND valid_to >= CURDATE()`。

## 字段

- `coupon_id`（int）：优惠券 ID，券的唯一标识，领券记录靠它关联回来。
- `coupon_name`（varchar(50)）：优惠券名称，用于展示，统计别拿名称当键。
- `coupon_type`（varchar(20)）：类型，full_reduction-满减、discount-折扣、no_threshold-无门槛，按类型拆分用这些值，例如 `WHERE coupon_type = 'full_reduction'`。
- `threshold_amt`（decimal(10,2)）：使用门槛金额，单位元，无门槛为 0，筛高门槛券就对它比大小。
- `face_value`（decimal(10,2)）：面额，单位元，折扣券这里是最高抵扣金额，算优惠力度时要区分券类型。
- `valid_from`（date）：有效期开始，判断某日是否可用的左边界。
- `valid_to`（date）：有效期结束，判断某日是否可用的右边界。

## 上下游

`mkt_coupon` 的数据来自业务系统（在线数据库），同步后进入数据平台，下游是 ODS 层的 `ods_mkt_coupon_di`，由它承接再往上层加工。表内有关联关系：`mkt_coupon_receive.coupon_id` 指向 `mkt_coupon.coupon_id`，领券记录据此带出券的规则。

## 使用注意

`mkt_coupon` 适合做券规则查询、按类型或门槛做券的归类，例如统计各 `coupon_type` 的券数量，或筛出某时间段有效的券。它只描述券的定义，不含领用和核销行为，想算领券量、核销率要去 `mkt_coupon_receive`。库里同家族还有噪声表：备份表 `mkt_coupon_bak`、带日期备份如 `mkt_coupon_bak_20250102`、旧版本 `mkt_coupon_v2`。它们是历史快照或早期口径，口径和时效都不保证，不能拿来跑统计，正式取数只认 `mkt_coupon`。
