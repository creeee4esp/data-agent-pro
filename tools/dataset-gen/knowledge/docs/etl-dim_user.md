# 加工说明：dim_user

## 加工逻辑

`dim_user` 为 DIM-用户维表，当前 2,000 行，粒度为用户，主键是 `user_id`。加工以用户表 `ods_user_info_di` 为主表，直取用户基础属性并派生若干维度字段。

年龄代际由 `birth_year` 经 `CASE` 派生：为空记"未知"，不小于 2000 记"00后"，不小于 1990 记"90后"，不小于 1980 记"80后"，其余记"80前"。大区由 `province` 经 `CASE` 映射，如上海市、江苏省、浙江省、安徽省、山东省归"华东"，广东省、福建省归"华南"，北京市、天津市、河北省归"华北"，湖北省、湖南省、河南省归"华中"，四川省、重庆市归"西南"，陕西省归"西北"，未命中归"其他"。注册日期取 `DATE(u.register_time)`。此外以相关子查询 `SELECT COUNT(*) FROM ods_user_address_di a WHERE a.user_id = u.user_id` 统计每位用户的收货地址数。

```sql
SELECT u.user_id, u.user_name, u.gender, CASE WHEN u.birth_year IS NULL THEN '未知' ... END,
  u.province, CASE u.province WHEN '上海市' THEN '华东' ... ELSE '其他' END, u.member_level, DATE(u.register_time),
  (SELECT COUNT(*) FROM ods_user_address_di a WHERE a.user_id = u.user_id)
FROM ods_user_info_di u
```

## 依赖与调度

`dim_user` 依赖两张上游表：收货地址表 `ods_user_address_di` 与用户表 `ods_user_info_di`。调度每日 02:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据。

## 质量检查

`dim_user` 发布前执行三项检查：主键 / 粒度唯一，确保 `user_id` 不重复；关键字段非空；行数与前一天相比波动超过 30% 告警。

检查未通过将影响下游。`user_id` 重复会使按用户关联的明细表出现一对多膨胀，用户数与金额被重复统计；大区、会员等级等字段为空会导致按这些维度汇总的报表丢失分组或归入空值；行数异常下降通常表明 `ods_user_info_di` 未抽取完整，部分用户在下游无法关联到维度属性。出现告警应先核查上游后再行发布。
