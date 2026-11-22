# 智能体知识评测语料

单个智能体的知识库：文档（Markdown）、QA / FAQ、业务知识，以 large 档（`enterprise_dw_l`）为"上线后的全部表"。

## 生成方式

- **事实由规则取出**（`facts.py`、`jobs.py`）：表与字段（干净元数据）、血缘 SQL（分层规则）、噪声表家族、枚举值、报告里的数字（直接查 `enterprise_dw_l`）、变更 / 事故 / 需求单的事件（固定种子随机）。
- **正文由大模型撰写**（写作要求见 `prompts.py`）：只能使用给定事实，数字原样照抄。当前提交的语料由 Kiro 子智能体撰写（`workbench.py` 导出任务批次，每个子智能体写一批并自查）；`build_knowledge.py` 是调用 DeepSeek API 的另一种方式，校验规则相同。
- **校验后落盘**（`build_knowledge.py`）：标题和二级标题、篇幅、必须出现的表名 / 字段 / 数字、不存在的表名、Markdown 表格、套话；QA 的 SQL 在 `enterprise_dw_l` 上执行，必须非空且不用噪声表；QA 问题与端到端评测的 72 题不能过于相似。不通过时把问题反馈给大模型重写，最多 3 轮。
- 大模型响应缓存在 `.llm-cache/`（不提交）；同样的请求只调用一次。

## 运行

需要 MySQL（已生成 large 档）。在 `tools/dataset-gen` 下：

```powershell
# 方式一：由 Kiro 子智能体撰写
python -m knowledge.workbench export            # 导出 28 个批次到 work/batches/
python -m knowledge.workbench check b01-table   # 每批写完后自查
python -m knowledge.workbench ingest            # 全部校验通过后写出 docs/ 和 jsonl

# 方式二：调用 DeepSeek API（需要环境变量 BENCHMARK_CHAT_API_KEY）
python -m knowledge.build_knowledge --workers 32          # 全部，约 10 分钟
python -m knowledge.build_knowledge --kinds table,qa --limit 3   # 试跑
python -m knowledge.build_knowledge --cache-only          # 只用缓存重建
```

## 输出

| 文件 | 内容 |
|---|---|
| `docs/*.md` | 文档，文件名 = `<类型>-<主题>.md` |
| `manifest.jsonl` | 文档元数据：文件、标题、二级标题、docType、涉及的表 / 指标、生效日期（只给评测器用，不参与向量化） |
| `qa_faq.jsonl` | QA（含经执行验证的 `sql`）和 FAQ |
| `business.jsonl` | 业务知识：businessTerm、description、synonyms |
| `failures.jsonl` | 未通过校验的任务（应为空） |

## 当前语料（2026-10-26）

| 类型 | 数量 | 父块（按二级标题估算） |
|---|---|---|
| 表说明 | 123 篇 | 492 |
| 指标说明 | 37 篇 | 111 |
| 加工说明 | 61 篇 | 183 |
| 规范手册 | 8 篇 | 80 |
| 主题月报 | 132 篇 | 396 |
| 经营周报 | 52 篇 | 208 |
| 变更公告 | 150 篇 | 150 |
| 数据事故报告 | 50 篇 | 100 |
| 取数需求单 | 300 篇 | 600 |
| 文档合计 | 913 篇，约 56 万字符 | 约 2,320 |
| QA / FAQ | 90 / 68 条 | — |
| 业务知识 | 60 条（单独检索） | — |

父块数按二级标题估算（超过 1,500 字再拆），实际以切块器的结果为准。
