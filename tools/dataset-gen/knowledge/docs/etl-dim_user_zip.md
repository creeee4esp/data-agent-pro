# 加工说明：dim_user_zip

## 加工逻辑

`dim_user_zip` 为 DIM-用户会员等级拉链表，当前 2,000 行。

要点：

- 数据源是用户表 `ods_user_info_di`，单表直取。
- 取字段：`user_id`、`member_level`。
- 生效日期取 `DATE(register_time)`。
- 失效日期固定写 `'9999-12-31'`，表示当前有效的开区间。

```sql
INSERT INTO dim_user_zip SELECT user_id, member_level, DATE(register_time), '9999-12-31' FROM ods_user_info_di
```

## 依赖与调度

`dim_user_zip` 依赖一张上游表。

- 上游表：用户表 `ods_user_info_di`。
- 调度：每日 02:00 开始。
- 触发：依赖上游全部成功后运行。
- 口径：处理前一天（T-1）的数据。

## 质量检查

`dim_user_zip` 发布前跑三项检查。

- 主键 / 粒度唯一。
- 关键字段非空。
- 行数与前一天相比波动超过 30% 告警。

失败影响：

- 粒度重复，按用户取当前等级时会取到多条，等级判断出错。
- `member_level` 为空，按会员等级分层的下游会丢分组。
- 行数骤降，说明 `ods_user_info_di` 没抽全，部分用户查不到等级记录。
