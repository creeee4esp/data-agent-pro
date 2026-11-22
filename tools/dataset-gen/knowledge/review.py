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

"""知识检索评测的标注复核（pooling）：把多种检索配置的前 N 条结果合并成候选池，由大模型判断每个候选能否单独回答查询，
能回答的作为可接受答案（alts）写回 queries.jsonl。

  python -m knowledge.review export <report.json> [<report.json> ...]   # 生成 work/rbatches/*.json
  python -m knowledge.review check <批次名>                             # 校验这一批的判定
  python -m knowledge.review ingest                                     # 写回 queries.jsonl 的 alts 字段

只复核单目标查询（多条类查询保持严格口径）。撰写者把每个任务的结果写到 work/rout/<任务 id>.json：
  {"answers": true, "keySentence": "..."}   # 文档类候选能回答时，keySentence 从候选原文逐字复制
  {"answers": false}
"""
import json
import re
import shutil
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORK = HERE / "work"
RBATCHES = WORK / "rbatches"
ROUT = WORK / "rout"
ABATCHES = WORK / "abatches"
AOUT = WORK / "aout"
POOL = WORK / "review_pool.json"
QUERIES_PER_BATCH = 15

RULES = """你在复核一个数据知识库检索评测的标注。每个任务给出一个用户问题和一段候选内容（文档的一节，或一条问答）。
请判断：只看这段候选内容，能否回答这个问题的核心诉求。
- 能回答：候选内容直接给出了问题要的事实、数字、做法或结论（不要求逐字相同，也不要求覆盖所有细节）。
- 不能回答：只是主题相关、只提到了相关的表或指标名、需要再看别的资料才能回答，或者回答的是另一个时间 / 另一张表 / 另一个指标。
时间、表名、周期（日 / 周 / 月）、指标必须对得上：问"1 月 13 日那周"，给的是 1 月 6 日那周，算不能回答；问周表，给的是日表，算不能回答。
输出只有一个 JSON 对象：能回答时 {"answers": true, "keySentence": "..."}，keySentence 是从候选内容中逐字复制的、最能回答问题的一句话
（10～80 字，原样保留标点和反引号；候选是问答时可以省略 keySentence）；不能回答时 {"answers": false}。"""


def norm(s):
    return re.sub(r"[\s`*]", "", s or "")


def load():
    queries = [json.loads(line) for line in (HERE / "queries.jsonl").read_text(encoding="utf-8").splitlines()]
    qa = {x["id"]: x for x in map(json.loads, (HERE / "qa_faq.jsonl").read_text(encoding="utf-8").splitlines())}
    manifest = {m["file"]: m for m in map(json.loads, (HERE / "manifest.jsonl").read_text(encoding="utf-8").splitlines())}
    return queries, qa, manifest


def is_target(item, target):
    if item["kind"] != target["kind"]:
        return False
    if item["kind"] == "qa":
        return item["id"] == target["id"]
    return item["file"] == target["file"] and norm(target["keySentence"]) in norm(item["text"])


def export(reports):
    queries, qa, manifest = load()
    by_query = defaultdict(list)
    for path in reports:
        for row in json.loads(Path(path).read_text(encoding="utf-8"))["results"]:
            by_query[row["id"]].extend(row.get("retrieved", []))
    pool = []
    for q in queries:
        if len(q["targets"]) != 1:
            continue
        target = q["targets"][0]
        seen_qa, docs = set(), defaultdict(list)
        for item in by_query.get(q["id"], []):
            if is_target(item, target):
                continue
            if item["kind"] == "qa":
                seen_qa.add(item["id"])
            elif item.get("file"):
                docs[item["file"]].append(item["text"])
        cands = [{"kind": "qa", "id": i} for i in sorted(seen_qa)]
        for file, texts in sorted(docs.items()):
            # 同一文件里被别的候选包含的片段（recursive 的碎块往往是父块的一部分）不再单独判定
            texts = sorted(set(texts), key=len, reverse=True)
            kept = []
            for t in texts:
                if not any(norm(t) in norm(k) for k in kept):
                    kept.append(t)
            cands += [{"kind": "doc", "file": file, "text": t} for t in kept]
        for i, c in enumerate(cands):
            c["task"] = f"{q['id']}-c{i:02d}"
            pool.append({"query": q["id"], **c})
    POOL.parent.mkdir(parents=True, exist_ok=True)
    POOL.write_text(json.dumps(pool, ensure_ascii=False, indent=1), encoding="utf-8")
    if RBATCHES.exists():
        shutil.rmtree(RBATCHES)
    RBATCHES.mkdir(parents=True)
    ROUT.mkdir(parents=True, exist_ok=True)
    qtext = {q["id"]: q["query"] for q in queries}
    qids = sorted({p["query"] for p in pool})
    for b, start in enumerate(range(0, len(qids), QUERIES_PER_BATCH), 1):
        chunk = set(qids[start:start + QUERIES_PER_BATCH])
        jobs = []
        for p in pool:
            if p["query"] not in chunk:
                continue
            if p["kind"] == "qa":
                x = qa[p["id"]]
                content = f"（问答）问：{x['question']}\n答：{x['answer'][:800]}"
            else:
                content = f"（文档《{manifest[p['file']]['title']}》的一节）\n{p['text'][:1800]}"
            jobs.append({"id": p["task"], "file": f"work/rout/{p['task']}.json",
                         "instruction": f"用户问题：{qtext[p['query']]}\n\n候选内容：\n{content}"})
        (RBATCHES / f"r{b:02d}.json").write_text(json.dumps({"batch": f"r{b:02d}", "rules": RULES, "jobs": jobs},
                                                            ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"r{b:02d}\t{len(jobs)} jobs")
    print(f"queries={len(qids)} candidates={len(pool)} {dict(Counter(p['kind'] for p in pool))}")


def _load_pool():
    return {p["task"]: p for p in json.loads(POOL.read_text(encoding="utf-8"))}


def _judge(p):
    path = ROUT / f"{p['task']}.json"
    if not path.exists():
        return None, ["missing file"]
    try:
        raw = path.read_text(encoding="utf-8").strip()
        obj = json.loads(raw[raw.find("{"):raw.rfind("}") + 1])
    except ValueError as e:
        return None, [f"cannot parse: {e}"]
    if not isinstance(obj.get("answers"), bool):
        return None, ["answers must be true or false"]
    if obj["answers"] and p["kind"] == "doc":
        k = obj.get("keySentence") or ""
        if not 8 <= len(norm(k)) <= 120:
            return None, [f"keySentence 长度不合适：{k!r}"]
        if norm(k) not in norm(p["text"]):
            return None, [f"keySentence 不是候选原文中的句子：{k[:40]}"]
    return obj, []


def check(name):
    pool = _load_pool()
    batch = json.loads((RBATCHES / f"{name}.json").read_text(encoding="utf-8"))
    bad = 0
    for j in batch["jobs"]:
        _, problems = _judge(pool[j["id"]])
        if problems:
            bad += 1
            print(f"FAIL {j['id']}: " + " | ".join(problems))
    print(f"{name}: ok={len(batch['jobs']) - bad} fail={bad}")
    return 1 if bad else 0

AUDIT_RULES = """你在做第二轮审核：第一轮已判定"这段候选内容能回答这个问题"，请严格复核，宁严勿宽。
只有同时满足以下全部条件才保留（keep = true）：
1. 用户读完这段候选内容，不需要再查任何别的资料，就能得到问题要的答案（要 SQL 的，候选里要有可直接用的 SQL 或完整的取数方法；要数字的，候选里要有那个数字；要结论的，候选里要有那个结论）。
2. 候选说的就是问题里问的那张表、那个指标、那个周期、那个时间；说的是另一张"类似的表"、另一个指标、另一个周期，一律不保留。
3. 只是解释了某个字段的含义、只是部分相关、只是给出了原则但没回答具体问题的，不保留。
输出只有一个 JSON 对象：{"keep": true} 或 {"keep": false}。"""


def export_audit():
    queries, qa, manifest = load()
    qtext = {q["id"]: q["query"] for q in queries}
    pool = _load_pool()
    jobs = []
    for p in sorted(pool.values(), key=lambda x: x["task"]):
        obj, problems = _judge(p)
        if problems or not obj["answers"]:
            continue
        if p["kind"] == "qa":
            x = qa[p["id"]]
            content = f"（问答）问：{x['question']}\n答：{x['answer'][:800]}"
        else:
            content = f"（文档《{manifest[p['file']]['title']}》的一节）\n{p['text'][:1800]}"
        jobs.append({"id": p["task"], "file": f"work/aout/{p['task']}.json",
                     "instruction": f"用户问题：{qtext[p['query']]}\n\n候选内容：\n{content}"})
    if ABATCHES.exists():
        shutil.rmtree(ABATCHES)
    ABATCHES.mkdir(parents=True)
    AOUT.mkdir(parents=True, exist_ok=True)
    size = 110
    for b, start in enumerate(range(0, len(jobs), size), 1):
        (ABATCHES / f"a{b:02d}.json").write_text(json.dumps({"batch": f"a{b:02d}", "rules": AUDIT_RULES,
                                                            "jobs": jobs[start:start + size]},
                                                           ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"a{b:02d}\t{len(jobs[start:start + size])} jobs")
    print(f"total {len(jobs)}")


def _audit(task):
    path = AOUT / f"{task}.json"
    if not path.exists():
        return None, ["missing file"]
    try:
        raw = path.read_text(encoding="utf-8").strip()
        obj = json.loads(raw[raw.find("{"):raw.rfind("}") + 1])
    except ValueError as e:
        return None, [f"cannot parse: {e}"]
    if not isinstance(obj.get("keep"), bool):
        return None, ["keep must be true or false"]
    return obj["keep"], []


def check_audit(name):
    batch = json.loads((ABATCHES / f"{name}.json").read_text(encoding="utf-8"))
    bad = 0
    for j in batch["jobs"]:
        _, problems = _audit(j["id"])
        if problems:
            bad += 1
            print(f"FAIL {j['id']}: " + " | ".join(problems))
    print(f"{name}: ok={len(batch['jobs']) - bad} fail={bad}")
    return 1 if bad else 0

def ingest():
    queries, _, _ = load()
    alts, fails, yes = defaultdict(list), [], Counter()
    for p in _load_pool().values():
        obj, problems = _judge(p)
        if problems:
            fails.append(p["task"])
            continue
        if obj["answers"]:
            keep, audit_problems = _audit(p["task"]) if AOUT.exists() else (True, [])
            if audit_problems:
                fails.append(p["task"])
                continue
            if not keep:
                yes["rejected_by_audit"] += 1
                continue
            yes[p["kind"]] += 1
            alt = {"kind": "qa", "id": p["id"]} if p["kind"] == "qa" else \
                {"kind": "doc", "file": p["file"], "keySentence": obj["keySentence"]}
            alts[p["query"]].append(alt)
    if fails:
        print(f"failures={len(fails)} {fails[:10]}")
        return 1
    for q in queries:
        q.pop("alts", None)
        if alts.get(q["id"]):
            q["alts"] = alts[q["id"]]
    (HERE / "queries.jsonl").write_text("".join(json.dumps(q, ensure_ascii=False) + "\n" for q in queries),
                                        encoding="utf-8")
    print(f"accepted={yes['qa'] + yes['doc']} {dict(yes)}; queries with alts={len(alts)}")
    return 0


if __name__ == "__main__":
    cmd = sys.argv[1]
    sys.exit({"export": lambda: export(sys.argv[2:]), "check": lambda: check(sys.argv[2]),
              "export-audit": lambda: export_audit(), "check-audit": lambda: check_audit(sys.argv[2]),
              "ingest": lambda: ingest()}[cmd]() or 0)
