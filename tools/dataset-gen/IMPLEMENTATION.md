# dataset-gen 实现说明

本文讲**代码是怎么实现的**：每一步做什么、关键算法、为什么这么做、怎么扩展。
用法与档位说明见 [README](README.md)，表结构与字段含义见 [SCHEMA.md](SCHEMA.md)。

> 状态说明：第 1～9 节描述**已实现**的 M0 代码（与源码一致）；第 10 节是 S2 规模化的**实现方案**，尚未编码。

---

## 1. 总体流程

```
gen.py
 ├─ prepare_database   DROP/CREATE enterprise_dw（仅当执行 core 步骤且 recreate=true）
 ├─ core      core_data.py     手写 DDL → 生成 CSV → LOAD DATA
 ├─ layers    layer_derive.py  ODS 规则派生 → 执行 ddl/layers/*.sql（DWD/DWS/ADS）
 ├─ noise     noise.py         从核心表复制出备份/临时/旧版本/测试/个人副本/月分表
 ├─ snapshot  degrade.py       保存干净元数据 → out/clean_metadata.json
 ├─ degrade   degrade.py       删注释/误标注释/删外键/噪声表列名缩写 → out/degrade_log.json
 └─ validate  validate.py      一致性/分层/规模/题集 → out/validate_report.json，失败则退出码 1
```

- 步骤顺序固定（`STEPS` 字典的插入顺序），`--steps` 只能挑选子集，不能改变顺序。
- **snapshot 必须在 degrade 之前**，否则保存的就不是干净元数据。
- `gen.py` 启动时 `chdir` 到自身目录，配置里的相对路径（`ddl/…`、`questions/…`、`out`）都以生成器目录为基准，从任何目录调用结果一致。
- 每步计时，最后打印 `timings(s)`，便于记录各步骤实测耗时。

### 1.1 可复现性设计

| 机制 | 说明 |
|---|---|
| 固定种子 | 所有随机数来自 `random.Random(seed)` 实例，不使用全局 `random` |
| 模块独立种子 | core 用 `seed`，noise 用 `seed+1`，degrade 用 `seed+2`。改噪声比例不会改变核心数据，改退化比例不会改变噪声表 |
| 确定的遍历顺序 | 表、外键都按名字排序后再遍历（`list_tables` 带 `ORDER BY`） |
| 固定时间戳 | ODS 的 `etl_time`/`dt` 用配置里的 `etl_date`，不用 `NOW()` |
| LLM 产物落盘 | 需要 LLM 生成的内容（S4 业务域 DDL）一次生成、提交入库，避免每次运行结果漂移 |

验证方式：同一配置生成两次，比对表清单、核心表 `CHECKSUM TABLE`、全部列注释的 MD5，结果应完全一致。

## 2. 数据库工具层 `gen/db.py`

| 函数 | 实现要点 |
|---|---|
| `connect` | PyMySQL，`utf8mb4`，`autocommit=True`，`local_infile=True`；密码可被环境变量 `DATASET_DB_PASSWORD` 覆盖 |
| `split_statements` | 极简 SQL 切分：跳过整行 `--` 注释和空行，**以行尾分号**作为语句结束。约定：DDL 字符串里不出现行尾分号 |
| `load_csv` | `LOAD DATA LOCAL INFILE`，逗号分隔、`OPTIONALLY ENCLOSED BY '"'`、`IGNORE 1 LINES` 跳过表头；CSV 中 `\N` 表示 NULL。导入期间会话级关闭 `foreign_key_checks`、`unique_checks`，`finally` 中恢复 |
| `table_group` | 表分组规则：在 `core_tables` 中 → `core`；前缀 `ods_/dwd_/dws_/ads_/dim_` → `derived`；其余 → `noise`。退化比例和统计都按这个分组 |
| `column_definitions` | 解析 `SHOW CREATE TABLE`，得到每列"去掉 COMMENT 子句"的完整定义。用于 `ALTER TABLE … MODIFY COLUMN <定义>` 删除注释而不改变类型、默认值、NULL 约束 |

`column_definitions` 的正则 `\s+COMMENT\s+'(?:[^'\\]|\\.|'')*'` 能处理注释里的转义引号和 `''`。

MySQL 服务端需要 `local_infile=1`（已写入本地 `my.ini`）。

## 3. 分布与词表 `gen/dist.py`

只用标准库实现，除数据库驱动和 YAML 解析外不引入额外依赖。

### 3.1 加权抽样 `weighted_sampler`

预先计算累积权重数组，抽样时取 `rng.random() * total`，用 `bisect` 二分查找落点。建表 O(n)，每次抽样 O(log n)。订单生成会调用几十万次，比 `rng.choices` 每次重算权重快得多。

### 3.2 用到的分布

| 分布 | 函数 | 参数 | 用在 |
|---|---|---|---|
| Zipf 长尾 | `zipf_weights(n, s=1.1)` | 第 k 名权重 `1/k^1.1` | 商品热度 |
| 日期权重 | `day_weights(year)` | 基础 1.0，全年线性增长到 1.3；周末 ×1.25；6/18 ×4、11/11 ×6、12/12 ×2.5；预热期 6/1～6/17 ×1.4、11/1～11/10 ×1.6 | 下单日期 |
| 日内双峰 | `HOUR_WEIGHTS` | 24 个权重，午间 12 点和晚间 20～21 点为峰 | 下单小时 |
| 对数均匀价格 | `money_cents(lo, hi)` | 在 `[ln lo, ln hi]` 均匀取值后取指数；尾数 2/3 概率 `.00`、1/3 概率 `.90` | 商品价格 |
| 人口加权 | `PROVINCES` | 16 个省市按人口权重 | 用户省份 |

词表：中文姓氏、名字用字、7 个一级品类 × 15 个二级品类（每个二级品类带价格区间和商品名词）、12 个虚构品牌、8 个拼音人名（用于个人副本表名）。

## 4. 核心业务库 `gen/core_data.py` + `ddl/core/core_m0.sql`

### 4.1 表结构

5 张表：`user_info`、`item_category`（两级，自引用外键）、`item_sku`、`trade_order`、`trade_order_item`。DDL 手写，带完整注释和外键，并**故意保留不规范命名**，用来考察 Schema Linking 对真实脏命名的鲁棒性：

- `trade_order.buyer_id` 关联 `user_info.user_id`（同一实体不同名）
- `discount_amt` 与 `pay_amount`、`total_amount` 缩写风格混用
- `qty`、`cate_level` 等缩写

### 4.2 ID 区间

不同实体的 ID 区间不重叠，便于肉眼识别、也避免 JOIN 错误时"碰巧对上"：

| 实体 | 区间 |
|---|---|
| 品类 | 一级 `10100, 10200…`；二级 = 一级 + 序号（`10101…`） |
| 商品 | `10,000,001` 起 |
| 用户 | `1,000,001` 起 |
| 订单 | `2,000,000,001` 起 |
| 明细 | `50,000,001` 起 |

### 4.3 生成顺序与算法

按依赖顺序生成：品类 → 商品 → 用户 → 订单+明细。

**金额全部用整数"分"计算**，写 CSV 时才格式化为 `元.分`。避免浮点误差导致"明细之和 ≠ 订单总额"。

1. **品类**：遍历 `CATEGORIES`，写一级和二级品类，记录叶子品类的价格区间和商品名词。
2. **商品**：随机选叶子品类 → 在其价格区间内 `money_cents` 定价 → 名称 = 品牌 + 名词 + 款式后缀；8% 下架；上架时间在数据年份之前 720 天内；按配置抽 N 个商品设为负库存（注入异常）。
3. **用户**：省份按人口加权、城市在省内随机（1% 置空，注入异常）；活跃分层决定下单权重和会员等级候选：

   | 分层 | 占比 | 下单权重 | 会员等级 |
   |---|---|---|---|
   | high | 10% | 8.0 | 4～5 |
   | normal | 40% | 2.0 | 2～3 |
   | low | 30% | 0.6 | 1～2 |
   | dormant | 20% | 0.1 | 1 |

   注册时间在 `year-2-01-01` ～ `year-12-01` 之间均匀分布；性别 M/F/U = 48/48/4；5% 出生年份为空。
4. **订单时间**：先一次性生成全部下单时间（日期按 `day_weights`、小时按 `HOUR_WEIGHTS`、分秒均匀），排序后按序分配订单号——订单号随时间递增，符合真实系统。
5. **每笔订单**：
   - **买家**：按下单权重抽用户；若该用户注册晚于下单时间就重抽（拒绝采样，最多 30 次，失败则取最早注册的用户），保证 `order_time ≥ register_time`。
   - **明细**：件数分布 1～5 件（55/25/12/5/3）；商品按 Zipf 热度抽、同一订单内去重。商品列表先 `shuffle` 再赋 Zipf 权重，避免"ID 小的商品天然热门"。每行购买数量 1～3（80/15/5）；`unit_price` = 商品当前价；`item_amount = qty × unit_price`。
   - **订单金额**：`total = Σ item_amount`；30% 订单有优惠，优惠 = 总额 × 5%～20%（取整到分，保证 `< total`）；`pay_amount = total - discount`。
   - **状态**：created 3、cancelled 7、paid 5、shipped 8、completed 72、refunded 5（权重）。paid/shipped/completed/refunded 有 `pay_time`，为下单后 10 秒～30 分钟；其余为空。
   - **收货省份**：90% 与买家常住省份相同，10% 随机。
6. **写 CSV**：`_CsvWriter` 把 `None` 写成 `\N`，记录行数。
7. **导入**：执行 DDL → 按外键依赖顺序 `LOAD DATA`。

### 4.4 已知简化

- `unit_price` 直接等于商品**当前**售价，没有历史价格、促销价。S2 加入价格历史表后改为按下单时刻取价。
- 订单状态与时间无关（例如年底的订单也可能已"完成"），S2 加入物流表后再细化。

## 5. 分层派生 `gen/layer_derive.py` + `ddl/layers/trade_m0.sql`

原则：下游一律在库内 `INSERT … SELECT` 从上游计算，保证分层数据与来源可对账。

### 5.1 ODS（规则生成）

对 `layers.ods_tables` 中每张表 `t`：

```sql
DROP TABLE IF EXISTS ods_{t}_di;
CREATE TABLE ods_{t}_di LIKE {t};          -- 复制列和索引，不复制外键
ALTER TABLE ods_{t}_di
  ADD COLUMN etl_time DATETIME NOT NULL COMMENT 'ETL 同步时间',
  ADD COLUMN dt DATE NOT NULL COMMENT '分区日期',
  COMMENT = 'ODS-<原表注释>';
INSERT INTO ods_{t}_di SELECT s.*, '<etl_date> 02:00:00', '<etl_date>' FROM {t} s;
```

`LIKE` 不复制外键，恰好符合"数仓层没有物理外键"的现实。

### 5.2 DWD / DWS / ADS（M0 手写 SQL）

| 表 | 来源 | 逻辑 |
|---|---|---|
| `dwd_trade_order_detail_di` | ODS 订单、明细、用户、商品、品类×2 | 明细粒度宽表；冗余会员等级、商品名、品牌、一二级品类；`is_paid = pay_time IS NOT NULL`；`order_date = DATE(order_time)` |
| `dws_trade_user_order_1d` | DWD | 按 `(order_date, buyer_id)` 汇总：下单数、支付订单数、支付金额 |
| `ads_trade_gmv_daily` | DWS | 按日汇总 GMV、支付订单数、支付买家数 |

**GMV 口径**（写在 SQL 文件头和 ADS 列注释里，也是题集 evidence 的口径）：已支付过（`pay_time` 非空，含后续退款）的订单明细金额之和，按下单日期统计。

由于全部从同一份核心数据计算，`ads` 的 GMV 总额必然等于从核心表直接算出的值——这条在 validate 中校验。

## 6. 噪声表 `gen/noise.py`

使用独立的 `random.Random(seed + 1)`。对 `noise.source_tables` 中每张源表依次按概率生成：

| 类型 | 命名 | 实现 | 数据量 |
|---|---|---|---|
| 备份 | `{t}_bak` | `CREATE TABLE … LIKE` + `INSERT … SELECT * … LIMIT` | `copy_rows` |
| 带日期备份 | `{t}_bak_YYYYMMDD`，每表 0～`dated_backups_max` 张 | 同上，日期在数据年份内随机 | `copy_rows` |
| 临时表 | `tmp_{表名最后一段}_{MMDD}` | `CREATE TABLE AS SELECT` 前若干列（去掉末尾 1～3 列） | `copy_rows × 2` |
| 旧版本 | `{t}_v1` 或 `{t}_old` | `LIKE` 后随机删 1～2 个非主键列，再按剩余列复制 | `copy_rows` |
| 测试表 | `test_{t}` | `LIKE`，不插数据 | 0 |

另外：

- **个人副本**：共 `personal_copies` 张，随机选源表，随机取 3～5 列（保持原列顺序），命名 `u_{拼音名}_{表名最后一段}_analysis`，`copy_rows / 2` 行。
- **按月分表**：`monthly_shards` 中配置的表按时间列拆成 `{t}_{YYYYMM}` 共 12 张，每张取该月最多 `shard_rows` 行。

区别：`LIKE` 保留主键和索引（看起来像正经表），`CREATE TABLE AS SELECT` 不带主键和索引（像临时表）——这和真实环境中两类表的特征一致。

命名冲突（比如两次抽到同一个个人副本名）时 `DROP TABLE IF EXISTS` 会覆盖前一张，最后的清单用 `set` 去重。

## 7. 快照与退化 `gen/degrade.py`

### 7.1 快照

从 `information_schema` 读取全部表注释、列（类型、注释）、外键，写入 `out/clean_metadata.json`：

```json
{"tables": {"trade_order": {"group": "core", "comment": "交易订单主表",
                            "columns": {"buyer_id": {"type": "bigint", "comment": "下单用户ID，…"}}}},
 "foreign_keys": [{"name": "fk_order_buyer", "table": "trade_order", "column": "buyer_id",
                   "ref_table": "user_info", "ref_column": "user_id"}]}
```

### 7.2 退化

使用独立的 `random.Random(seed + 2)`，按表名顺序遍历，每张表：

1. 按分组比例 `drop_table_comment[group]` 决定是否 `ALTER TABLE … COMMENT = ''`。
2. 遍历有注释的列：
   - 按 `drop_column_comment[group]` 删注释：`MODIFY COLUMN <去掉 COMMENT 的原定义>`；
   - 未删除、注释含"金额"且类型为 DECIMAL 的列，按 `wrong_comment` 比例在注释后追加"（单位：分）"——实际存的是元，模拟过时或错误的注释。
   - 同一张表的所有修改合并成一条 `ALTER TABLE`。
3. 只对 noise 组：列名按词表缩写（`amount→amt`、`price→prc`、`time→tm`…，每列只缩写一个词），新名不能与已有列重名；**单独执行一条 `ALTER … RENAME COLUMN`**，避免和同列的 `MODIFY` 冲突。
4. 最后遍历核心表的外键，按 `drop_core_fk` 比例 `DROP FOREIGN KEY`。

所有操作计数和改名明细写入 `out/degrade_log.json`。

## 8. 校验 `gen/validate.py`

| 类别 | 内容 | 判定 |
|---|---|---|
| 一致性（13 条） | 明细金额 = 数量×单价；单价 = 商品价；订单总额 = 明细之和；实付 = 总额−优惠；0 ≤ 优惠 < 总额；已支付状态 ⇔ 有支付时间；支付晚于下单；下单晚于注册；每单至少一条明细；4 类孤儿记录（含商品必须挂在二级品类） | 每条 SQL 返回违例行数，必须为 0 |
| 分层一致性（3 条） | DWD 行数 = 明细行数；ADS GMV 总额 = 核心表计算值；ADS 支付订单数 = 核心表计算值 | 两值相等 |
| 注入异常 | 城市为空数、负库存数 | 只统计，不判失败 |
| 规模统计 | 各分组表数、列数、表/列注释覆盖率；外键数；核心表行数 | 只统计 |
| 题集 | 每道题执行 `gold_sql`，结果非空且至少有一个非 NULL 值；每条 `alt_sqls` 的结果必须与标准答案一致 | 任一失败即失败 |

**结果比对的归一化**（`_norm_result`）：数值统一转 `Decimal` 并保留两位小数（`3`、`3.0`、`Decimal('3.00')` 视为相同），日期转 ISO 字符串；`order_matters=false` 时整体排序后比较。这也是后续端到端评测的判分基础。

任何一致性、分层、题集检查失败，`gen.py` 以退出码 1 结束，可直接用于 CI。

## 9. 题集 `questions/*.jsonl`

每行一道题，字段见 README。编写流程：

1. 只基于核心表写 `gold_sql`；时间条件用左闭右开区间（`>= '2025-06-01' AND < '2025-07-01'`），避免 `DATETIME` 边界问题。
2. 若派生层也能回答，把派生层写法放进 `alt_sqls`（例如查 `ads_trade_gmv_daily`），validate 会保证它和标准答案一致。
3. `evidence` 写清口径，对应项目里的业务知识。
4. `tables`/`columns` 填标准 SQL 用到的表和列，供召回评测计算召回率。
5. 有排序和 LIMIT 的题设 `order_matters=true`，并在排序里加唯一的兜底键（如 `ORDER BY total_qty DESC, sku_id`），避免并列导致结果不确定。
6. 跑 `python gen.py --config config/m0.yaml --steps validate` 确认通过。

## 10. S2 规模化实现方案（未实现）

目标：表数从 44 扩到 3,000+，且仍然一条命令生成、可复现、可校验。

### 10.1 核心表扩充

在 `ddl/core/` 新增支付、物流、售后、优惠券、访问日志、库存流水等表（共 15～20 张），在 `core_data.py` 中按依赖顺序追加生成函数，并补充对应的一致性约束（如退款 ≤ 实付、签收晚于发货）。

### 10.2 分层派生规则化

把 M0 手写的 DWS 改为由配置驱动的模板，新增 `gen/layer_rules.py`：

```yaml
layers:
  dws:
    - source: dwd_trade_order_detail_di
      date_col: order_date
      grains: {user: buyer_id, sku: sku_id, cate1: category1_id, prov: receiver_province}
      periods: [1d, 7d, 30d]
      measures:
        order_cnt: "COUNT(DISTINCT order_id)"
        paid_amount: "SUM(IF(is_paid = 1, item_amount, 0))"
```

生成规则：每个 `grain × period` 组合生成一张 `dws_{域}_{grain}_order_{period}`；`1d` 按天分组，`7d`/`30d` 按"统计日期往前 N 天的滚动窗口"计算。表数 = Σ(粒度数 × 周期数)。ADS 同理按主题模板生成。DIM 由实体表生成维度宽表和拉链表。

### 10.3 噪声表放大

- 噪声的源表从"核心表"扩展为"核心表 + 派生表"（约 270 张），带日期备份每表 2～3 张，贡献约 700 张；
- 月分表扩展到约 10 张日志/流水表 × 36 个月，约 360 张；
- 其余类型按比例；不够再调大配置比例。
- 预计合计 2,000～3,000 张（规划值，以实测为准）。

### 10.4 性能与资源

- 噪声表数据量小，瓶颈在 DDL 次数（每张表几次 DDL）。若 3,000 张表耗时过长，改为多连接并行创建（噪声表之间没有依赖）。
- MySQL 已调大 `table_open_cache`、`table_definition_cache` 到 8,000；磁盘预估约 0.5～1GB。
- `validate` 中按表统计行数的查询需要改为抽样，或用 `information_schema.TABLES.TABLE_ROWS` 近似值。

### 10.5 待修复：复制取样的确定性

当前噪声表用 `INSERT … SELECT * … LIMIT n` 复制数据，没有 `ORDER BY`。InnoDB 全表扫描实际按主键顺序返回，所以 M0 实测可复现，但 SQL 语义上不保证。S2 改为按主键排序（`ORDER BY 1`，源表第一列均为主键）。

### 10.6 召回衰减实验

用 `m0`、`s2-medium`、`s2-large` 三档配置分别生成，同一题集跑召回评测，观察表规模对召回指标的影响，作为召回改造的基线。

## 11. 如何扩展

| 想做的事 | 改哪里 |
|---|---|
| 加一张核心表 | `ddl/core/*.sql` 加 DDL；`core_data.py` 加生成逻辑并在 `run()` 中按依赖顺序导入；`config.core_tables` 加表名；`validate.CONSISTENCY` 加约束 |
| 加一张派生表 | `ddl/layers/*.sql` 加 `CREATE` + `INSERT … SELECT`（只能读上游表）；需要的话在 `LAYER_CHECKS` 加对账 |
| 加一种噪声 | `noise.run()` 中加一个分支；`config.noise` 加比例参数 |
| 加一种退化 | `degrade.run()` 中加规则并计入 `log`；注意不能改变题集引用的核心/派生表列名 |
| 加题 | `questions/*.jsonl` 追加一行，跑 `--steps validate` |
| 换规模 | 复制一份配置文件修改 `scale`、比例和种子 |

纯函数（切分 SQL、分组、分布、缩写、结果归一化）的单元测试在 `tests/test_pure.py`，修改这些函数后运行 `python -m unittest discover -s tests`。
