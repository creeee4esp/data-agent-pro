# Copyright 2024-2026 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""S2 数据集统计与图表：读取三个档位的 validate 报告、clean_metadata 和召回评测报告，输出 Markdown 和 PNG。

运行（tools/dataset-gen 目录）：python report_s2.py
输出：out/s2-report/stats.md、*.png
依赖：matplotlib（只用于画图，生成器本身不需要）
"""
import json
import re
from collections import Counter
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

plt.rcParams["font.sans-serif"] = ["Microsoft YaHei", "SimHei"]
plt.rcParams["axes.unicode_minus"] = False

HERE = Path(__file__).resolve().parent
OUT = HERE / "out" / "s2-report"
TIERS = [("small", "enterprise_dw_s"), ("medium", "enterprise_dw_m"), ("large", "enterprise_dw_l")]
BENCH = HERE.parent.parent / "data-agent-management" / "target" / "schema-benchmark"


def kind_of(name, group):
    """表的细分类型（用于构成图）。"""
    if group == "core":
        return "核心表"
    if group == "derived":
        return name.split("_")[0].upper() + " 层"
    if re.search(r"_\d{8}$", name) and "_bak_" not in name:
        return "按日分表"
    if re.search(r"_\d{6}$", name):
        return "按月分表"
    if "_bak" in name:
        return "备份表"
    if name.startswith("tmp_"):
        return "临时表"
    if name.startswith("test_"):
        return "测试表"
    if name.startswith("u_"):
        return "个人副本"
    if re.search(r"_(v1|v2|old|his)$", name):
        return "旧版本"
    return "其他噪声"


def latest_report(label):
    dirs = sorted(d for d in BENCH.glob(f"*-{label}") if (d / "report.json").exists())
    return json.loads((dirs[-1] / "report.json").read_text(encoding="utf-8")) if dirs else None


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    md = ["# S2 数据集统计", ""]
    tier_stats, kinds = {}, {}
    for tier, _ in TIERS:
        base = HERE / "out" / f"s2-{tier}"
        v = json.loads((base / "validate_report.json").read_text(encoding="utf-8"))
        meta = json.loads((base / "clean_metadata.json").read_text(encoding="utf-8"))
        tier_stats[tier] = v
        kinds[tier] = Counter(kind_of(n, t["group"]) for n, t in meta["tables"].items())

    # ---- 规模与质量表 ----
    md += ["## 规模与质量校验", "",
           "| 档位 | 表 | 列 | 核心 / 派生 / 噪声 | 一致性违例 | 分层对账 | 题目可执行 |", "|---|---|---|---|---|---|---|"]
    for tier, _ in TIERS:
        v = tier_stats[tier]
        s, g = v["stats"], v["stats"]["by_group"]
        lc = v["layers"]
        qs = v["questions"]
        md.append(f"| {tier} | {s['total_tables']} | {s['total_columns']} | {g['core']['tables']} / "
                  f"{g['derived']['tables']} / {g['noise']['tables']} | {sum(v['consistency'].values())}"
                  f"（{len(v['consistency'])} 条约束） | {sum(x['ok'] for x in lc.values())}/{len(lc)} | "
                  f"{sum(q['ok'] for q in qs)}/{len(qs)} |")
    md += ["", "## 元数据覆盖率（退化后）", "", "| 档位 | 分组 | 表注释覆盖率 | 列注释覆盖率 |", "|---|---|---|---|"]
    for tier, _ in TIERS:
        for grp, x in tier_stats[tier]["stats"]["by_group"].items():
            md.append(f"| {tier} | {grp} | {x['table_comment_coverage']:.0%} | {x['column_comment_coverage']:.0%} |")
    md += ["", "## 核心表行数（三档相同）", "", "| 表 | 行数 |", "|---|---|"]
    for t, n in tier_stats["large"]["stats"]["core_rows"].items():
        md.append(f"| {t} | {n:,} |")

    # ---- 图 1：表构成 ----
    order = ["核心表", "ODS 层", "DIM 层", "DWD 层", "DWS 层", "ADS 层", "备份表", "按月分表", "按日分表", "旧版本",
             "临时表", "测试表", "个人副本", "其他噪声"]
    fig, ax = plt.subplots(figsize=(9, 4.5))
    bottom = [0] * len(TIERS)
    cmap = plt.get_cmap("tab20")
    for i, k in enumerate(order):
        vals = [kinds[t].get(k, 0) for t, _ in TIERS]
        if sum(vals):
            ax.bar([t for t, _ in TIERS], vals, bottom=bottom, label=k, color=cmap(i))
            bottom = [a + b for a, b in zip(bottom, vals)]
    for i, total in enumerate(bottom):
        ax.text(i, total, f"{total}", ha="center", va="bottom")
    ax.set_ylabel("表数量")
    ax.set_title("三档数据集的表构成")
    ax.legend(bbox_to_anchor=(1.01, 1), loc="upper left", fontsize=8)
    fig.tight_layout()
    fig.savefig(OUT / "table_composition.png", dpi=130)
    plt.close(fig)

    md += ["", "## 表构成", "", "![表构成](table_composition.png)", "",
           "| 类型 | " + " | ".join(t for t, _ in TIERS) + " |", "|---|" + "---|" * len(TIERS)]
    for k in order:
        if any(kinds[t].get(k) for t, _ in TIERS):
            md.append(f"| {k} | " + " | ".join(str(kinds[t].get(k, 0)) for t, _ in TIERS) + " |")

    # ---- 题集分布 ----
    qs = [json.loads(line) for line in (HERE / "questions" / "s2.jsonl").read_text(encoding="utf-8").splitlines() if line]
    tags = Counter(t for q in qs for t in q["tags"])
    diffs = Counter(q["difficulty"] for q in qs)
    ntables = Counter(len(q["tables"]) for q in qs)
    fig, axes = plt.subplots(1, 3, figsize=(12, 3.6))
    axes[0].barh(list(tags), list(tags.values()), color="#4C72B0")
    axes[0].set_title("题型标签（可多选）")
    axes[1].bar(sorted(diffs), [diffs[k] for k in sorted(diffs)], color="#55A868")
    axes[1].set_title("难度")
    axes[2].bar([str(k) for k in sorted(ntables)], [ntables[k] for k in sorted(ntables)], color="#C44E52")
    axes[2].set_title("标准 SQL 用到的表数")
    fig.suptitle(f"S2 题集：{len(qs)} 道")
    fig.tight_layout()
    fig.savefig(OUT / "question_distribution.png", dpi=130)
    plt.close(fig)
    md += ["", f"## 题集（{len(qs)} 道）", "", "![题集分布](question_distribution.png)", "",
           "标签：" + "、".join(f"{k} {v}" for k, v in sorted(tags.items())),
           "", "难度：" + "、".join(f"{k} {v}" for k, v in sorted(diffs.items())),
           "", "标准 SQL 的表数：" + "、".join(f"{k} 张 {v} 道" for k, v in sorted(ntables.items())),
           "", f"带替代写法的题：{sum(1 for q in qs if q['alt_sqls'])} 道"]

    # ---- 召回衰减曲线 ----
    reports = {t: latest_report(f"s2-{t}-fixed") for t, _ in TIERS}
    if all(reports.values()):
        x = [reports[t]["run"]["tableCount"] for t, _ in TIERS]
        fig, ax = plt.subplots(figsize=(7, 4.2))
        for k, style in (("5", "o--"), ("10", "o-"), ("20", "o:")):
            ax.plot(x, [reports[t]["summary"]["overall"]["fullHitRate"][k] * 100 for t, _ in TIERS], style,
                    label=f"全命中率@{k}")
        ax.plot(x, [reports[t]["summary"]["overall"]["tableRecall"]["10"] * 100 for t, _ in TIERS], "s-",
                color="gray", alpha=0.6, label="表召回率@10")
        ax.set_xscale("log")
        ax.set_xticks(x)
        ax.set_xticklabels([f"{t}\n{n} 张" for (t, _), n in zip(TIERS, x)])
        ax.set_ylabel("%")
        ax.set_ylim(0, 100)
        ax.set_title("legacy 召回随表数量的衰减（72 题）")
        ax.legend()
        ax.grid(alpha=0.3)
        fig.tight_layout()
        fig.savefig(OUT / "recall_decay.png", dpi=130)
        plt.close(fig)

        tag_names = sorted(reports["small"]["summary"]["byTag"])
        fig, ax = plt.subplots(figsize=(9, 4))
        w = 0.27
        for i, (t, _) in enumerate(TIERS):
            ax.bar([j + (i - 1) * w for j in range(len(tag_names))],
                   [reports[t]["summary"]["byTag"][g]["fullHitRate"]["10"] * 100 for g in tag_names], w, label=t)
        ax.set_xticks(range(len(tag_names)))
        ax.set_xticklabels(tag_names, rotation=15)
        ax.set_ylabel("全命中率@10 (%)")
        ax.set_title("按题型的全命中率@10")
        ax.legend()
        fig.tight_layout()
        fig.savefig(OUT / "recall_by_tag.png", dpi=130)
        plt.close(fig)

        md += ["", "## legacy 召回基线（衰减曲线）", "", "![衰减曲线](recall_decay.png)", "",
               "| 档位 | 表 | 全命中@5 | 全命中@10 | 全命中@20 | 召回率@10 | 初始化 | 初始化模型调用 | P50 / P95 ms | 可复现 |",
               "|---|---|---|---|---|---|---|---|---|---|"]
        for t, _ in TIERS:
            r = reports[t]
            o = r["summary"]["overall"]
            md.append(f"| {t} | {r['run']['tableCount']} | {o['fullHitRate']['5']:.1%} | {o['fullHitRate']['10']:.1%} "
                      f"| {o['fullHitRate']['20']:.1%} | {o['tableRecall']['10']:.1%} | {r['run']['initMillis'] / 1000:.0f} s "
                      f"| {r['run']['initEmbeddingCalls']} | {o['p50Millis']} / {o['p95Millis']} | "
                      f"{'是' if r['run']['deterministic'] else '否'} |")
        md += ["", "![按题型](recall_by_tag.png)", "", "| 题型 | " + " | ".join(t for t, _ in TIERS) + " |",
               "|---|" + "---|" * len(TIERS)]
        for g in tag_names:
            md.append(f"| {g} | " + " | ".join(
                f"{reports[t]['summary']['byTag'][g]['fullHitRate']['10']:.0%}" for t, _ in TIERS) + " |")
    else:
        md += ["", "（召回评测报告不全，跳过衰减曲线）"]

    (OUT / "stats.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    print(f"written {OUT}")


if __name__ == "__main__":
    main()
