# 加工说明：ods_user_address_di

## 加工逻辑

`ods_user_address_di`（ODS-用户收货地址表）把业务库 `user_address` 原样抽取到贴源层，不做清洗与聚合，仅追加加工时间 `etl_time` 和分区 `dt`：

```sql
INSERT INTO ods_user_address_di
SELECT user_address.*, <固定 etl_time>, <分区 dt>
FROM user_address
```

有一处容易误用：收货地址是会被用户反复修改的，`ods_user_address_di` 是按天抽取的快照，同一地址在不同 `dt` 分区里可能内容不同，跨分区做地址统计时务必带上 `dt` 过滤，否则同一条地址会被重复计入。当前产出约 4,017 行。

## 依赖与调度

`ods_user_address_di` 依赖业务库用户收货地址表 `user_address`。每日 01:00 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据，写入当天 `dt` 分区。

## 质量检查

`ods_user_address_di` 产出后执行三项检查：主键 / 粒度唯一、关键字段非空、行数与前一天相比波动超过 30% 告警。任一项失败，都会影响需要关联收货地址的下游加工，例如按收货省份统计的汇总表会因地址缺失或重复而口径失真。
