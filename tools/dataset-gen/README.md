# dataset-gen

企业级 NL2SQL 评测数据集生成器：用规则生成一个模拟中型企业数仓的 MySQL 库（核心业务表 + ODS/DIM/DWD/DWS/ADS 分层 + 大量备份、分表、临时等噪声表 + 元数据退化），以及带标准答案的评测题集。所有数据由固定种子生成，任何机器上生成的结果逐字节一致。

- 实现细节：[IMPLEMENTATION.md](IMPLEMENTATION.md)
- 表和字段含义（M0）：[SCHEMA.md](SCHEMA.md)

## 档位

| 配置 | 数据库 | 表数 | 列数 | 生成耗时* | 用途 |
|---|---|---|---|---|---|
| `config/m0.yaml` | `enterprise_dw` | 44 | 349 | 约 4 s | 最小链路，S1 基线 |
| `config/s2-small.yaml` | `enterprise_dw_s` | 116 | 766 | 约 15 s | S2 小档 |
| `config/s2-medium.yaml` | `enterprise_dw_m` | 558 | 3,101 | 约 30 s | S2 中档 |
| `config/s2-large.yaml` | `enterprise_dw_l` | 3,298 | 20,806 | 约 100 s | S2 大档（企业规模） |

\* 本机实测（笔记本，本地 MySQL）。三个 S2 档位通过 `extends: s2-base.yaml` 共享完全相同的核心表、核心数据、退化规则和题集，只有派生层广度和噪声表数量不同（控制变量）。

题集：`questions/m0.jsonl`（10 道）、`questions/s2.jsonl`（72 道，含 m0 的 10 道，由 `questions/build_s2.py` 生成）。

## 在新电脑上复现

### 1. 准备环境

| 软件 | 版本 | 说明 |
|---|---|---|
| MySQL | 8.0.x | 需要开启 `local_infile`；建议用专用实例，生成器会 DROP 上表中的四个库 |
| Python | 3.10+（本机为 3.13） | |
| 磁盘 | 约 2 GB | large 档约 1 GB |

### 2. 配置 MySQL

在 `my.ini`（Windows）或 `my.cnf`（Linux/macOS）的 `[mysqld]` 段加入后重启：

```ini
[mysqld]
local_infile=1                 # 必需：核心数据用 LOAD DATA LOCAL INFILE 导入
table_open_cache=8000          # large 档有 3,000+ 张表
table_definition_cache=8000
character-set-server=utf8mb4
default-time-zone=+08:00
```

不想改配置文件，也可以在 MySQL 客户端里临时设置（重启失效）：

```sql
SET GLOBAL local_infile = 1;
SET GLOBAL table_open_cache = 8000;
SET GLOBAL table_definition_cache = 8000;
```

确认：`SELECT @@local_infile;` 返回 1。

账号默认 `root` / `root`、地址 `127.0.0.1:3306`。不同时：

- 地址、用户名：改 `config/m0.yaml` 和 `config/s2-base.yaml` 的 `db` 段；
- 密码：设置环境变量，不要写进配置文件：
  ```powershell
  $env:DATASET_DB_PASSWORD = "你的密码"      # PowerShell
  ```
  ```bash
  export DATASET_DB_PASSWORD='你的密码'      # bash
  ```

### 3. 安装依赖

```bash
git clone https://github.com/creeee4esp/data-agent-pro.git
cd data-agent-promax/tools/dataset-gen
python -m pip install -r requirements.txt            # 生成器只依赖 PyMySQL、PyYAML
python -m pip install -r requirements-report.txt     # 可选：统计图表需要 matplotlib
```

### 4. 一键生成

```bash
python build_all.py                  # 题集 + m0 + 三个 S2 档位，共约 3 分钟
python build_all.py m0 s2-small      # 只生成部分档位
python build_all.py --report         # 生成后输出统计报告和图表
```

`build_all.py` 依次执行下面的命令，任何一步失败立即停止。也可以手动逐步运行：

```bash
python questions/build_s2.py                          # 生成 questions/s2.jsonl（已提交到仓库，可跳过）
python gen.py --config config/m0.yaml
python gen.py --config config/s2-small.yaml
python gen.py --config config/s2-medium.yaml
python gen.py --config config/s2-large.yaml
python report_s2.py                                   # 可选
```

### 5. 确认结果

每个档位最后的 `[validate]` 输出应当满足：

```
consistency violations: 0
layer checks: N/N ok
questions: 72/72 ok          （m0 为 10/10）
```

`gen.py` 在任何校验失败时以退出码 1 结束。参考数值（S2 large）：

```
tables=3298 columns=20806
consistency violations: 0     （28 条业务约束）
layer checks: 145/145 ok      （DWS/ADS 指标总额与来源 DWD、核心表对账）
questions: 72/72 ok
```

可复现性检查：同一配置生成两次，比较表清单、`CHECKSUM TABLE`、列注释 MD5 应完全一致。

### 6. 单元测试

```bash
python -m unittest discover -s tests
```

## 输出文件（`out/<档位>/`，不进 git）

| 文件 | 内容 |
|---|---|
| `csv/` | 核心表的原始 CSV |
| `clean_metadata.json` | 退化前的完整元数据（表/列注释、外键），可作为"补全描述"类实验的标准答案 |
| `degrade_log.json` | 删了哪些注释、改了哪些列名 |
| `layer_manifest.json` | 规则生成的派生表清单和对账 SQL |
| `noise_tables.json` | 噪声表清单 |
| `validate_report.json` | 校验结果、规模统计、每道题的执行结果预览 |
| `out/s2-report/` | `report_s2.py` 生成的 `stats.md` 和图表 |

## 流程

| 步骤 | 模块 | 作用 |
|---|---|---|
| core | `gen/core_data.py` | 5 张基础核心表：生成 CSV，`LOAD DATA` 导入 |
| core_ext | `gen/core_ext.py` | S2：11 张扩展核心表（支付、退款、物流、售后、评价、优惠券、访问等），库内 `INSERT…SELECT` 派生，确定性哈希代替随机数 |
| layers | `gen/layer_derive.py` + `gen/layer_rules.py` | ODS 规则派生 + M0 手写分层；S2 再按"事件 × 粒度 × 周期"模板生成 DWS，按主题生成 ADS |
| noise | `gen/noise.py`（M0）/ `gen/noise_v2.py`（S2） | 备份、带日期备份、临时、旧版本、测试、个人副本、按月/按日分表；v2 先确定性规划再多连接并行建表 |
| snapshot | `gen/degrade.py` | 保存干净元数据 |
| degrade | `gen/degrade.py` | 删注释、删外键、注释误标、噪声表列名缩写；S2 每张表独立随机，三档核心表退化结果一致 |
| validate | `gen/validate.py` | 业务一致性、分层对账、规模统计、题集可执行性 |

## 题集格式（`questions/*.jsonl`）

| 字段 | 说明 |
|---|---|
| `id` / `question` / `evidence` | 题号、问题、口径说明（对应项目的业务知识） |
| `gold_sql` | 标准 SQL，只引用核心表 |
| `alt_sqls` | 其他正确写法（如查 ADS/DWD），校验时要求结果与标准 SQL 一致 |
| `tables` / `columns` | 标准 SQL 用到的表和列，用于计算召回率；Java 评测器加载时会与解析 `gold_sql` 的结果交叉校验 |
| `difficulty` / `tags` | L1～L4；题型标签：value_match、metric、ambiguous_table、multi_hop、explicit_name、time_reasoning |
| `order_matters` | 判分时是否比较行顺序 |

新增题目：编辑 `questions/build_s2.py` 中的 `NEW` 列表，运行它重新生成 `s2.jsonl`，再跑任一档位的 `--steps validate` 确认可执行。

## 用生成的库跑召回评测（Java）

需要本地 Ollama 和 `qwen3-embedding:0.6b`（`ollama pull qwen3-embedding:0.6b`），在仓库根目录：

```powershell
.\mvnw.cmd -pl data-agent-management test "-Dtest=SchemaRecallBenchmarkIT" "-Djacoco.skip=true" "-Dbenchmark.schema=enterprise_dw_s" "-Dbenchmark.questions=../tools/dataset-gen/questions/s2.jsonl" "-Dbenchmark.label=s2-small"
```

- 只需要传 `-Dbenchmark.schema`，连接串由程序拼出。**不要在 Windows 命令行传带 `&` 的 JDBC URL**：`mvnw.cmd` 会把 `&` 当成命令分隔符，后面的参数全部丢失。
- 报告在 `data-agent-management/target/schema-benchmark/`；参数说明见 `BenchmarkConfig`。
- large 档首次初始化较慢（3,000+ 张表、约 2.4 万个 schema 文档逐条写入内存向量库），可放在后台运行；Embedding 结果有本地缓存，第二次起明显加快。

## 常见问题

| 现象 | 原因与处理 |
|---|---|
| `Loading local data is disabled` | 服务端没开 `local_infile`，见第 2 步 |
| `Too many open files` / 建表很慢 | 调大 `table_open_cache`、`table_definition_cache` |
| Windows 控制台打印中文报 `UnicodeEncodeError` | `$env:PYTHONIOENCODING="utf-8"`；生成的文件本身都是 UTF-8 |
| 想换种子或规模 | 复制一份配置改 `seed` / `scale`；三个 S2 档位要一起改 `s2-base.yaml` 才能保持控制变量 |
