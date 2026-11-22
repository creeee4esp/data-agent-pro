# 变更公告：提醒：备份 / 副本表列名与正表不一致（dws_trade_cate2_order_1m_bak_20250801）

## 变更说明

`dws_trade_cate2_order_1m_bak_20250801` 为带日期备份表，其列名与正表不一致，需提醒使用方注意。

### 关键信息

- 正表列名：`category2_name`。
- 副本列名：`category2_nm`。
- 含义相同，仅字段名有别。
- 生效日期：`2025-04-15`。

### 影响与建议

- 影响：引用 `dws_trade_cate2_order_1m_bak_20250801` 的查询可能因列名不符报错。
- 建议：查该备份时列名写 `category2_nm`，正式分析以正表 `category2_name` 为准。

流量数据组发布。
