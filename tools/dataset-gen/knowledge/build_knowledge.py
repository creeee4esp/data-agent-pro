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

"""生成单个智能体的知识库评测语料：文档（Markdown）、QA / FAQ、业务知识。

事实由 facts.py / jobs.py 按规则取出，正文全部由大模型撰写；本脚本只负责并发调用、校验和落盘。
校验不通过时把问题反馈给大模型重写，最多 3 轮；仍不通过的写入 failures.jsonl。

用法（在 tools/dataset-gen 下）：
  python -m knowledge.build_knowledge                    # 全部
  python -m knowledge.build_knowledge --kinds table,qa --limit 3   # 试跑：每类 3 个
  python -m knowledge.build_knowledge --cache-only       # 只用缓存重建输出
"""
import argparse
import json
import re
import sys
import threading
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import pymysql

from .facts import Facts
from .jobs import JobBuilder
from .llm import FatalLlmError, LlmClient
from .prompts import build_messages, feedback_message

HERE = Path(__file__).resolve().parent
OUT = HERE
IDENT = re.compile(r"(?<![A-Za-z0-9_])((?:ods|dim|dwd|dws|ads|tmp|test|u)_[a-z0-9_]+)")
SQL_TABLE = re.compile(r"\b(?:FROM|JOIN)\s+`?([a-z][a-z0-9_]*)`?", re.I)
BOILERPLATE = ("本文档由", "如有疑问请联系", "本报告由")
MAX_ROUNDS = 3


class Validator:

    def __init__(self, facts: Facts):
        self.f = facts
        self.tables = set(facts.tables)
        self.effective = set(facts.effective)
        self.local = threading.local()
        qs = [json.loads(line)["question"] for line in
              (HERE.parent / "questions" / "s2.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
        self.eval_bigrams = [self._bigrams(q) for q in qs]

    @staticmethod
    def _bigrams(s):
        s = re.sub(r"\s+", "", s)
        return {s[i:i + 2] for i in range(len(s) - 1)}

    def _unknown_tables(self, text):
        bad = []
        for tok in IDENT.findall(text):
            tok = tok.rstrip("_")
            if tok in self.tables or any(t.startswith(tok + "_") for t in self.tables):
                continue
            if tok not in bad:
                bad.append(tok)
        return bad

    def markdown(self, job, text):
        problems = []
        lines = text.strip().splitlines()
        if not lines or lines[0].strip() != f"# {job['title']}":
            problems.append(f"第一行必须是：# {job['title']}")
        heads = [ln[3:].strip() for ln in lines if ln.startswith("## ")]
        if heads != job["sections"]:
            problems.append(f"二级标题必须依次是 {job['sections']}，实际是 {heads}")
        if text.lstrip().startswith("---"):
            problems.append("不要 YAML 头部")
        n = len(text)
        if n < job["min_chars"] * 0.85 or n > job["max_chars"] * 1.25:
            problems.append(f"篇幅 {n} 字符，要求 {job['min_chars']}～{job['max_chars']}")
        missing = [s for s in job["required"] if s not in text]
        if missing:
            problems.append(f"以下内容必须原样出现（表名、字段名或数字）：{missing[:15]}")
        if not job["allow_md_table"] and any(ln.lstrip().startswith("|") for ln in lines):
            problems.append("不要使用 Markdown 表格，改用列表")
        bad = self._unknown_tables(text)
        if bad:
            problems.append(f"出现了事实里没有的表名：{bad[:8]}，只能使用事实中的表")
        if any(b in text for b in BOILERPLATE):
            problems.append("删掉套话")
        return problems

    def _conn(self):
        if not hasattr(self.local, "conn"):
            self.local.conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="root",
                                              database=self.f.schema, charset="utf8mb4", autocommit=True)
        return self.local.conn

    def json_obj(self, job, obj):
        problems = []
        for k in job["fields"]:
            if not obj.get(k):
                problems.append(f"缺少字段 {k}")
        if problems:
            return problems
        text = json.dumps(obj, ensure_ascii=False)
        missing = [s for s in job["required"] if s not in text]
        if missing:
            problems.append(f"以下内容必须出现：{missing}")
        bad = self._unknown_tables(text)
        if bad:
            problems.append(f"出现了不存在的表名：{bad[:8]}")
        if job["kind"] == "qa":
            problems += self._check_sql(obj["sql"])
            q = self._bigrams(obj["question"])
            if any(len(q & e) / max(1, len(q | e)) >= 0.5 for e in self.eval_bigrams):
                problems.append("question 与评测题过于相似，请换一个分析需求")
            if not 200 * 0.8 <= len(obj["answer"]) <= 1600:
                problems.append(f"answer 长度 {len(obj['answer'])}，要求 200～600 字加 SQL")
        elif job["kind"] == "faq":
            if not 60 <= len(obj["answer"]) <= 600:
                problems.append(f"answer 长度 {len(obj['answer'])}，要求 80～400 字")
        elif job["kind"] == "business":
            syn = obj["synonyms"]
            if not isinstance(syn, list) or not 1 <= len(syn) <= 6:
                problems.append("synonyms 必须是 2～5 个字符串的数组")
            elif obj["businessTerm"] in syn:
                problems.append("synonyms 不能包含 businessTerm 本身")
            if re.fullmatch(r"[a-z_]+", obj["businessTerm"]):
                problems.append("businessTerm 要用中文业务名词")
        return problems

    def _check_sql(self, sql):
        s = sql.strip().rstrip(";")
        if not re.match(r"^(SELECT|WITH)\b", s, re.I):
            return ["sql 必须是 SELECT 语句"]
        used = {t.lower() for t in SQL_TABLE.findall(s)}
        noise = [t for t in used if t in self.tables and t not in self.effective]
        if noise:
            return [f"sql 用了噪声表 {noise}，必须用正表"]
        try:
            with self._conn().cursor() as cur:
                cur.execute("SET SESSION max_execution_time = 20000")
                cur.execute(s)
                rows = cur.fetchmany(5)
        except Exception as e:  # noqa: BLE001 -- 把数据库报错原样反馈给大模型
            return [f"sql 执行失败：{str(e)[:300]}"]
        if not rows:
            return ["sql 执行结果为空，请调整条件使结果不为空"]
        return []


def parse_json(text):
    t = text.strip()
    t = re.sub(r"^```(?:json)?\s*|\s*```$", "", t)
    start, end = t.find("{"), t.rfind("}")
    return json.loads(t[start:end + 1])


def strip_fence(text):
    t = text.strip()
    m = re.match(r"^```(?:markdown|md)?\s*\n(.*)\n```$", t, re.S)
    return (m.group(1) if m else t).strip() + "\n"


STOP = threading.Event()


def run_job(job, llm, validator):
    """返回 (job, 结果或 None, 轮数, 问题列表)；调用大模型出错时问题列表里是错误信息。"""
    if STOP.is_set():
        return job, None, 0, ["skipped: stopped after a fatal LLM error"]
    try:
        return _run_job(job, llm, validator)
    except FatalLlmError as e:
        STOP.set()
        return job, None, 0, [f"fatal: {e}"]
    except Exception as e:  # noqa: BLE001 -- 单个任务出错不影响其他任务
        return job, None, 0, [f"error: {str(e)[:300]}"]


def _run_job(job, llm, validator):
    messages = build_messages(job)
    max_tokens = 16000 if job["kind"] == "manual" else 8000
    problems = []
    for round_no in range(1, MAX_ROUNDS + 1):
        raw = llm.chat(messages, max_tokens=max_tokens)
        try:
            if job["output"] == "md":
                result = strip_fence(raw)
                problems = validator.markdown(job, result)
            else:
                result = parse_json(raw)
                problems = validator.json_obj(job, result)
        except (ValueError, KeyError, TypeError) as e:
            result, problems = None, [f"输出无法解析为要求的格式：{e}"]
        if not problems:
            return job, result, round_no, []
        messages = messages + [{"role": "assistant", "content": raw}, feedback_message(problems)]
    return job, None, MAX_ROUNDS, problems


def write_outputs(docs_done, qa_done, biz_done, failures):
    doc_dir = OUT / "docs"
    doc_dir.mkdir(exist_ok=True)
    manifest = []
    for job, text in sorted(docs_done, key=lambda x: x[0]["id"]):
        name = job["id"][len("doc-"):] + ".md"
        (doc_dir / name).write_text(text, encoding="utf-8")
        manifest.append({"file": f"docs/{name}", "title": job["title"], "sections": job["sections"], **job["meta"]})
    (OUT / "manifest.jsonl").write_text("".join(json.dumps(m, ensure_ascii=False) + "\n" for m in manifest),
                                        encoding="utf-8")
    rows = []
    for job, obj in sorted(qa_done, key=lambda x: x[0]["id"]):
        row = {"id": job["id"], "type": job["kind"].upper(), "title": obj["title"], "question": obj["question"],
               "answer": obj["answer"], **job["meta"]}
        if job["kind"] == "qa":
            row["sql"] = obj["sql"]
        rows.append(row)
    (OUT / "qa_faq.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
    rows = [{"id": job["id"], "businessTerm": obj["businessTerm"], "description": obj["description"],
             "synonyms": obj["synonyms"], **job["meta"]} for job, obj in sorted(biz_done, key=lambda x: x[0]["id"])]
    (OUT / "business.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows),
                                        encoding="utf-8")
    (OUT / "failures.jsonl").write_text("".join(json.dumps(f, ensure_ascii=False) + "\n" for f in failures),
                                        encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--kinds", help="只生成这些 kind，逗号分隔")
    ap.add_argument("--limit", type=int, help="每个 kind 最多生成几个（试跑用）")
    ap.add_argument("--workers", type=int, default=16)
    ap.add_argument("--cache-only", action="store_true")
    args = ap.parse_args()

    facts = Facts()
    docs, qa_faq, biz = JobBuilder(facts).all_jobs()
    jobs = docs + qa_faq + biz
    if args.kinds:
        kinds = set(args.kinds.split(","))
        jobs = [j for j in jobs if j["kind"] in kinds]
    if args.limit:
        per = Counter()
        picked = []
        for j in jobs:
            if per[j["kind"]] < args.limit:
                per[j["kind"]] += 1
                picked.append(j)
        jobs = picked
    print(f"jobs: {len(jobs)} {dict(Counter(j['kind'] for j in jobs))}", flush=True)

    llm = LlmClient(HERE / ".llm-cache", cache_only=args.cache_only)
    validator = Validator(facts)
    docs_done, qa_done, biz_done, failures, rounds = [], [], [], [], Counter()
    started = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(run_job, j, llm, validator) for j in jobs]
        for i, fut in enumerate(as_completed(futures), 1):
            job, result, round_no, problems = fut.result()
            if result is None:
                failures.append({"id": job["id"], "problems": problems})
            elif job["output"] == "md":
                docs_done.append((job, result))
            elif job["kind"] == "business":
                biz_done.append((job, result))
            else:
                qa_done.append((job, result))
            rounds[round_no] += 1
            if i % 25 == 0 or i == len(jobs):
                s = llm.stats
                print(f"[{i}/{len(jobs)}] {time.time() - started:.0f}s ok={len(jobs) - len(failures)} "
                      f"fail={len(failures)} calls={s['calls']} hits={s['hits']} "
                      f"tokens={s['prompt_tokens']}/{s['completion_tokens']}", flush=True)
    if STOP.is_set():
        print("stopped early: fatal LLM error (see failures.jsonl); finished results are cached", flush=True)
    write_outputs(docs_done, qa_done, biz_done, failures)
    print(f"done: docs={len(docs_done)} qa_faq={len(qa_done)} business={len(biz_done)} failures={len(failures)} "
          f"rounds={dict(sorted(rounds.items()))}", flush=True)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
