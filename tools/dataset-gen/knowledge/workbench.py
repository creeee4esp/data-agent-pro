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

"""由 Kiro 子智能体撰写正文时用的工作台：导出任务批次、校验某一批、汇总落盘。

  python -m knowledge.workbench export            # 生成 work/batches/*.json（每批约 5 万字符的输出量）
  python -m knowledge.workbench check <批次名>     # 校验这一批已写好的文件，列出缺失和问题
  python -m knowledge.workbench ingest            # 校验全部，写 docs/、manifest.jsonl、qa_faq.jsonl、business.jsonl

撰写者把第 i 个任务的结果写到 work/out/<任务 id>.md（文档）或 .json（QA / FAQ / 业务知识，只含一个 JSON 对象）。
"""
import json
import shutil
import sys
from collections import Counter
from pathlib import Path

from .build_knowledge import Validator, parse_json, strip_fence, write_outputs
from .facts import Facts
from .jobs import JobBuilder
from .prompts import SYSTEM, build_messages

HERE = Path(__file__).resolve().parent
WORK = HERE / "work"
BATCHES = WORK / "batches"
OUTDIR = WORK / "out"
JSON_EST = {"qa": 1300, "faq": 450, "business": 350}
BATCH_CHARS = 50000
BATCH_INPUT_CHARS = 100000


def _all_jobs():
    docs, qa_faq, biz = JobBuilder(Facts()).all_jobs()
    return docs + qa_faq + biz


def _est(job):
    if job["output"] == "md":
        return (job["min_chars"] + job["max_chars"]) // 2
    return JSON_EST[job["kind"]]


def export():
    if BATCHES.exists():
        shutil.rmtree(BATCHES)
    BATCHES.mkdir(parents=True)
    OUTDIR.mkdir(parents=True, exist_ok=True)
    jobs = _all_jobs()
    # 每批的输出量约 5 万字符、输入（任务说明）不超过约 10 万字符，并且同一批只放一种 kind
    batches, cur, size, size_in, kind = [], [], 0, 0, None
    for j in jobs:
        n_in = len(build_messages(j)[1]["content"])
        if cur and (size + _est(j) > BATCH_CHARS or size_in + n_in > BATCH_INPUT_CHARS or j["kind"] != kind):
            batches.append(cur)
            cur, size, size_in = [], 0, 0
        cur.append(j)
        size += _est(j)
        size_in += n_in
        kind = j["kind"]
    if cur:
        batches.append(cur)
    names = []
    for i, b in enumerate(batches, 1):
        name = f"b{i:02d}-{b[0]['kind']}"
        items = [{"id": j["id"], "output": j["output"], "file": f"work/out/{j['id']}.{j['output']}",
                  "instruction": build_messages(j)[1]["content"]} for j in b]
        (BATCHES / f"{name}.json").write_text(json.dumps({"batch": name, "rules": SYSTEM, "jobs": items},
                                                         ensure_ascii=False, indent=1), encoding="utf-8")
        names.append((name, len(b), sum(_est(j) for j in b)))
    for n, c, s in names:
        print(f"{n}\t{c} jobs\t~{s // 1000}k chars")
    print(f"total {len(jobs)} jobs in {len(names)} batches")


def _validate(job, validator):
    path = OUTDIR / f"{job['id']}.{job['output']}"
    if not path.exists():
        return None, ["missing file"]
    raw = path.read_text(encoding="utf-8")
    try:
        if job["output"] == "md":
            result = strip_fence(raw)
            return result, validator.markdown(job, result)
        result = parse_json(raw)
        return result, validator.json_obj(job, result)
    except (ValueError, KeyError, TypeError) as e:
        return None, [f"cannot parse: {e}"]


def check(batch_name):
    facts = Facts()
    validator = Validator(facts)
    batch = json.loads((BATCHES / f"{batch_name}.json").read_text(encoding="utf-8"))
    wanted = {j["id"] for j in batch["jobs"]}
    jobs = [j for j in _all_jobs() if j["id"] in wanted]
    bad = 0
    for j in jobs:
        _, problems = _validate(j, validator)
        if problems:
            bad += 1
            print(f"FAIL {j['id']}: " + " | ".join(problems))
    print(f"{batch_name}: ok={len(jobs) - bad} fail={bad}")
    return 1 if bad else 0


def ingest():
    facts = Facts()
    validator = Validator(facts)
    docs_done, qa_done, biz_done, failures = [], [], [], []
    for j in _all_jobs():
        result, problems = _validate(j, validator)
        if problems:
            failures.append({"id": j["id"], "problems": problems})
        elif j["output"] == "md":
            docs_done.append((j, result))
        elif j["kind"] == "business":
            biz_done.append((j, result))
        else:
            qa_done.append((j, result))
    if (HERE / "docs").exists():
        shutil.rmtree(HERE / "docs")
    write_outputs(docs_done, qa_done, biz_done, failures)
    print(f"docs={len(docs_done)} qa_faq={len(qa_done)} business={len(biz_done)} failures={len(failures)}")
    print(dict(Counter(f["id"].split("-")[1] for f in failures)))
    return 1 if failures else 0


if __name__ == "__main__":
    cmd = sys.argv[1]
    sys.exit({"export": lambda: export(), "check": lambda: check(sys.argv[2]), "ingest": lambda: ingest()}[cmd]() or 0)
