# 变更公告：提醒：备份 / 副本表列名与正表不一致（u_lisi_warehouse_di_analysis）

## 变更说明

`u_lisi_warehouse_di_analysis` 这张副本表的列名和正表对不上，想提前跟大家说一声，从 2025-08-11 起按此提醒执行，由交易数据组发布。

具体是这样：正表里叫 `etl_time` 的列，到了副本 `u_lisi_warehouse_di_analysis` 里被写成了 `etl_tm`。字段含义是一样的，就是名字不同，直接拿正表的 SQL 过来跑会报列不存在。

影响的主要是那些直接查这张副本的脚本和临时分析。大家在用 `u_lisi_warehouse_di_analysis` 时，记得把 `etl_time` 换成 `etl_tm`；如果不是非用副本不可，建议还是回正表取数，省得来回对名字。有疑问的作业麻烦自查一遍。
