# 变更公告：新表上线（dwd_trade_pay_di）

## 变更说明

`dwd_trade_pay_di`（DWD-支付明细）上线，作为支付明细层的统一来源支撑下游加工。

### 关键信息

- 新表：`dwd_trade_pay_di`，支付明细。
- 上游：`ods_pay_order_di`、`ods_trade_order_di`。
- 生效日期：`2025-03-14`。

### 影响与建议

- 影响：新增明细层产出，不改动现有表。
- 建议：支付相关的下游汇总与分析统一以 `dwd_trade_pay_di` 为明细来源。

商品数据组发布。
