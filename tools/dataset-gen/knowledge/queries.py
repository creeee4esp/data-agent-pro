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

"""知识检索评测查询：按规则选目标、分配查询类型，正文（查询句和关键句）由大模型撰写，再统一校验。

  python -m knowledge.queries export          # 生成 work/qbatches/*.json
  python -m knowledge.queries check <批次名>   # 校验这一批
  python -m knowledge.queries ingest          # 校验全部，写 queries.jsonl

撰写者把每个任务的结果写到 work/qout/<任务 id>.json：
  {"query": "...", "keySentence": "..."}   # keySentence 只有文档类目标需要（从该节原文逐字摘一句）
"""
import json
import random
import re
import shutil
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORK = HERE / "work"
QBATCHES = WORK / "qbatches"
QOUT = WORK / "qout"
SEED = 20261026
BATCH_INPUT_CHARS = 20000

# 每种文档抽多少节做目标
DOC_QUOTA = {"表说明": 50, "指标说明": 30, "加工说明": 25, "规范手册": 20, "主题月报": 30, "经营周报": 20,
             "变更公告": 25, "数据事故报告": 20, "取数需求单": 40}
QA_QUOTA, FAQ_QUOTA, MULTI_QUOTA, NONE_QUOTA = 50, 40, 20, 20

TYPES = {
    "精确": "使用目标里的术语、表名或指标名原词提问，像熟悉数据的同事那样直接问。",
    "同义": "换成业务方常用的同义说法提问：不能出现目标里反引号包裹的任何表名、字段名，也不能直接照抄目标标题里的关键词。",
    "口语": "用不懂数据的业务同事的大白话提问，可以带一点场景：不能出现任何表名、字段名或英文标识符。",
    "代码": "围绕一个具体的表名、字段名或枚举值提问（例如问某个字段的含义、某张表能不能用），查询里必须包含目标里出现过的一个标识符。",
    "辨析": "问两个容易混淆的概念有什么区别、该用哪个。",
    "多条": "一个问题需要同时用到给出的两段内容才能答全（例如既要知道指标怎么算，又要知道取哪张表）。",
    "无关": "写一个数据团队知识库里不可能有答案的问题，主题与电商数据完全无关（例如行政、人事、IT 报修、差旅报销），"
            "不要出现订单、商品、用户、支付、物流、指标、报表等词。",
}

COMMON_RULES = """你在为一个数据知识库的检索评测编写查询。读者是使用数据智能体的业务方和分析师，他们会向智能体提问，智能体要从知识库里找到能回答问题的内容。
要求：
1. query 是用户会真实提出的一个问题，6～60 个字，自然、具体，不要写成"请告诉我某某文档的内容"。
2. 不同任务的问题之间不要用同一个句式开头。
3. 只依据给出的目标内容，不要编造目标里没有的事实。
4. 输出只有一个 JSON 对象，写到任务指定的文件。"""

IDENT = re.compile(r"`([^`]+)`")
SNAKE = re.compile(r"\b[a-z][a-z0-9]*(?:_[a-z0-9]+)+\b")


def norm(s):
    return re.sub(r"\s+", "", s.replace("`", "").replace("*", ""))


def sections_of(text):
    """[(二级标题, 该节全文)]。"""
    parts = re.split(r"(?m)^## ", text)
    out = []
    for p in parts[1:]:
        head, _, body = p.partition("\n")
        out.append((head.strip(), body.strip()))
    return out


def load_corpus():
    manifest = [json.loads(line) for line in (HERE / "manifest.jsonl").read_text(encoding="utf-8").splitlines()]
    docs = []
    for m in manifest:
        text = (HERE / m["file"]).read_text(encoding="utf-8")
        docs.append((m, sections_of(text)))
    qa_faq = [json.loads(line) for line in (HERE / "qa_faq.jsonl").read_text(encoding="utf-8").splitlines()]
    return docs, qa_faq


def _pick_type(r, text, choices):
    if any(SNAKE.search(x) for x in IDENT.findall(text)) and "代码" not in choices:
        choices = choices + ["代码"]
    return r.choice(choices)


def build_jobs():
    r = random.Random(SEED)
    docs, qa_faq = load_corpus()
    jobs = []
    by_type = defaultdict(list)
    for m, secs in docs:
        for head, body in secs:
            if len(body) >= 60:
                by_type[m["docType"]].append((m, head, body))
    for dt, quota in DOC_QUOTA.items():
        for m, head, body in r.sample(by_type[dt], min(quota, len(by_type[dt]))):
            t = _pick_type(r, body, ["精确", "同义", "口语"])
            jobs.append({"id": f"q-doc-{len(jobs) + 1:03d}", "type": t, "docType": dt,
                         "targets": [{"kind": "doc", "file": m["file"], "title": m["title"], "section": head,
                                      "text": body}]})
    qas = [x for x in qa_faq if x["type"] == "QA"]
    faqs = [x for x in qa_faq if x["type"] == "FAQ"]
    for x in r.sample(qas, QA_QUOTA):
        jobs.append({"id": f"q-qa-{len(jobs) + 1:03d}", "type": r.choice(["精确", "同义", "口语"]), "docType": "QA",
                     "targets": [{"kind": "qa", "id": x["id"], "title": x["title"], "question": x["question"],
                                  "text": x["answer"][:900]}]})
    for x in r.sample(faqs, FAQ_QUOTA):
        t = "辨析" if x.get("category") == "易混指标" else _pick_type(r, x["answer"], ["精确", "同义", "口语"])
        jobs.append({"id": f"q-faq-{len(jobs) + 1:03d}", "type": t, "docType": "FAQ",
                     "targets": [{"kind": "qa", "id": x["id"], "title": x["title"], "question": x["question"],
                                  "text": x["answer"]}]})
    # 多条：指标说明的一节 + 该指标出现的表说明的一节
    metric_docs = {m["metrics"][0]: (m, secs) for m, secs in docs if m["docType"] == "指标说明" and m.get("metrics")}
    table_docs = {m["tables"][0]: (m, secs) for m, secs in docs if m["docType"] == "表说明"}
    pairs = []
    for key, (mm, msecs) in sorted(metric_docs.items()):
        tables = [t for t in table_docs if key in "".join(b for _, b in table_docs[t][1])]
        if tables:
            pairs.append((mm, msecs, table_docs[r.choice(sorted(tables))]))
    for mm, msecs, (tm, tsecs) in r.sample(pairs, min(MULTI_QUOTA, len(pairs))):
        a = next(((h, b) for h, b in msecs if h == "计算公式"), msecs[0])
        b = next(((h, s) for h, s in tsecs if h == "使用注意"), tsecs[-1])
        jobs.append({"id": f"q-multi-{len(jobs) + 1:03d}", "type": "多条", "docType": "多条",
                     "targets": [{"kind": "doc", "file": mm["file"], "title": mm["title"], "section": a[0], "text": a[1]},
                                 {"kind": "doc", "file": tm["file"], "title": tm["title"], "section": b[0],
                                  "text": b[1]}]})
    for _ in range(NONE_QUOTA):
        jobs.append({"id": f"q-none-{len(jobs) + 1:03d}", "type": "无关", "docType": "无关", "targets": []})
    return jobs


def instruction(job):
    lines = [f"查询类型：{job['type']}。{TYPES[job['type']]}"]
    need_key = [t for t in job["targets"] if t["kind"] == "doc"]
    for i, t in enumerate(job["targets"], 1):
        if t["kind"] == "doc":
            lines.append(f"\n目标 {i}（文档《{t['title']}》的章节「{t['section']}」）：\n{t['text']}")
        else:
            lines.append(f"\n目标 {i}（问答：{t['title']}）：\n问：{t['question']}\n答：{t['text']}")
    if need_key:
        if len(need_key) == 1:
            lines.append("\n输出 {\"query\": \"...\", \"keySentence\": \"...\"}。keySentence 是从目标章节原文中逐字复制的一句话"
                         "（10～80 字，原样保留标点和反引号），它正是回答这个问题最关键的那句。")
        else:
            lines.append("\n输出 {\"query\": \"...\", \"keySentences\": [\"目标1的关键句\", \"目标2的关键句\"]}，每个关键句都从对应"
                         "目标章节原文中逐字复制（10～80 字，原样保留标点和反引号）。")
    else:
        lines.append("\n输出 {\"query\": \"...\"}。")
    return "\n".join(lines)


def export():
    if QBATCHES.exists():
        shutil.rmtree(QBATCHES)
    QBATCHES.mkdir(parents=True)
    QOUT.mkdir(parents=True, exist_ok=True)
    jobs = build_jobs()
    batches, cur, size = [], [], 0
    for j in jobs:
        ins = instruction(j)
        if cur and size + len(ins) > BATCH_INPUT_CHARS:
            batches.append(cur)
            cur, size = [], 0
        cur.append({"id": j["id"], "file": f"work/qout/{j['id']}.json", "instruction": ins})
        size += len(ins)
    if cur:
        batches.append(cur)
    for i, b in enumerate(batches, 1):
        (QBATCHES / f"q{i:02d}.json").write_text(json.dumps({"batch": f"q{i:02d}", "rules": COMMON_RULES, "jobs": b},
                                                            ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"q{i:02d}\t{len(b)} jobs")
    print(f"total {len(jobs)} {dict(Counter(j['type'] for j in jobs))}")


def _bigram_jaccard(a, b):
    a, b = norm(a), norm(b)
    x = {a[i:i + 2] for i in range(len(a) - 1)}
    y = {b[i:i + 2] for i in range(len(b) - 1)}
    return len(x & y) / max(1, len(x | y))


def validate(job, obj):
    problems = []
    q = (obj.get("query") or "").strip()
    if not 6 <= len(q) <= 80:
        problems.append(f"query 长度 {len(q)}，要求 6～60 字")
    target_text = " ".join(t["text"] for t in job["targets"])
    idents = {x for x in IDENT.findall(target_text) if SNAKE.fullmatch(x.strip())}
    used = [x for x in idents if x in q]
    if job["type"] in ("同义", "口语") and used:
        problems.append(f"{job['type']}类查询不能出现标识符：{used[:5]}")
    if job["type"] == "口语" and re.search(r"[A-Za-z_]{3,}", q):
        problems.append("口语类查询不能出现英文标识符")
    if job["type"] == "代码" and not any(x in q for x in idents | set(SNAKE.findall(target_text))):
        problems.append("代码类查询必须包含目标里出现过的一个表名、字段名或枚举值")
    if job["type"] == "无关" and re.search(r"订单|商品|用户|支付|物流|指标|报表|GMV|退款|销售|[a-z]+_[a-z]+", q):
        problems.append("无关类查询不能出现电商数据相关的词")
    for t in job["targets"]:
        if t["kind"] == "qa" and _bigram_jaccard(q, t["question"]) >= 0.6:
            problems.append("query 与目标问答的问题太像，请换个问法")
        if q and norm(q) == norm(t["title"]):
            problems.append("query 不能等于标题")
    docs = [t for t in job["targets"] if t["kind"] == "doc"]
    keys = obj.get("keySentences") if len(docs) > 1 else ([obj.get("keySentence")] if docs else [])
    if docs:
        if not isinstance(keys, list) or len(keys) != len(docs):
            problems.append("缺少关键句（单目标用 keySentence，多目标用 keySentences 数组）")
        else:
            for t, k in zip(docs, keys):
                if not k or not 8 <= len(norm(k)) <= 120:
                    problems.append(f"关键句长度不合适：{k!r}")
                elif norm(k) not in norm(t["text"]):
                    problems.append(f"关键句不是《{t['title']}》「{t['section']}」原文中的句子：{k[:40]}")
    return problems, keys


def _load(job):
    path = QOUT / f"{job['id']}.json"
    if not path.exists():
        return None, ["missing file"]
    try:
        raw = path.read_text(encoding="utf-8").strip()
        raw = re.sub(r"^```(?:json)?\s*|\s*```$", "", raw)
        return json.loads(raw[raw.find("{"):raw.rfind("}") + 1]), []
    except ValueError as e:
        return None, [f"cannot parse: {e}"]


def check(name):
    batch = json.loads((QBATCHES / f"{name}.json").read_text(encoding="utf-8"))
    wanted = {j["id"] for j in batch["jobs"]}
    bad = 0
    for job in build_jobs():
        if job["id"] not in wanted:
            continue
        obj, problems = _load(job)
        if obj is not None:
            problems, _ = validate(job, obj)
        if problems:
            bad += 1
            print(f"FAIL {job['id']}: " + " | ".join(problems))
    print(f"{name}: ok={len(wanted) - bad} fail={bad}")
    return 1 if bad else 0


def ingest():
    rows, fails = [], []
    for job in build_jobs():
        obj, problems = _load(job)
        keys = None
        if obj is not None:
            problems, keys = validate(job, obj)
        if problems:
            fails.append(job["id"])
            continue
        targets = []
        docs = iter(keys or [])
        for t in job["targets"]:
            if t["kind"] == "doc":
                targets.append({"kind": "doc", "file": t["file"], "section": t["section"], "keySentence": next(docs)})
            else:
                targets.append({"kind": "qa", "id": t["id"]})
        rows.append({"id": job["id"], "query": obj["query"].strip(), "type": job["type"], "docType": job["docType"],
                     "targets": targets})
    (HERE / "queries.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows),
                                        encoding="utf-8")
    print(f"queries={len(rows)} failures={len(fails)} {fails[:10]}")
    print(dict(Counter(r["type"] for r in rows)), dict(Counter(r["docType"] for r in rows)))
    return 1 if fails else 0


if __name__ == "__main__":
    cmd = sys.argv[1]
    sys.exit({"export": lambda: export(), "check": lambda: check(sys.argv[2]), "ingest": lambda: ingest()}[cmd]() or 0)
