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

"""用 DeepSeek API 补完第二轮审核里还没有结果的任务（与子智能体用同一份审核标准和任务说明）。

  python -m knowledge.review_api [--workers 8]
"""
import argparse
import json
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from .llm import FatalLlmError, LlmClient

HERE = Path(__file__).resolve().parent
ABATCHES = HERE / "work" / "abatches"


def pending_jobs():
    jobs, rules = [], None
    for f in sorted(ABATCHES.glob("a0*.json")):
        batch = json.loads(f.read_text(encoding="utf-8"))
        rules = batch["rules"]
        jobs += [j for j in batch["jobs"] if not (HERE / j["file"]).exists()]
    return rules, jobs


def judge(llm, rules, job):
    messages = [{"role": "system", "content": rules}, {"role": "user", "content": job["instruction"]}]
    for _ in range(3):
        raw = llm.chat(messages, max_tokens=4000, temperature=0.0)
        m = re.search(r"\{[^{}]*\"keep\"\s*:\s*(true|false)[^{}]*\}", raw)
        if m:
            keep = m.group(1) == "true"
            (HERE / job["file"]).write_text(json.dumps({"keep": keep}), encoding="utf-8")
            return keep
        messages = messages + [{"role": "assistant", "content": raw},
                               {"role": "user", "content": "只输出 {\"keep\": true} 或 {\"keep\": false}。"}]
    raise RuntimeError(f"unparseable output for {job['id']}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--workers", type=int, default=8)
    args = ap.parse_args()
    rules, jobs = pending_jobs()
    print(f"pending {len(jobs)}", flush=True)
    llm = LlmClient(HERE / ".llm-cache")
    keep = fail = 0
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(judge, llm, rules, j): j for j in jobs}
        for i, fut in enumerate(as_completed(futures), 1):
            try:
                keep += fut.result()
            except FatalLlmError:
                raise
            except Exception as e:  # noqa: BLE001 -- 单条失败不影响其他任务，汇总后报告
                fail += 1
                print(f"FAIL {futures[fut]['id']}: {e}", flush=True)
            if i % 50 == 0 or i == len(jobs):
                print(f"[{i}/{len(jobs)}] keep={keep} fail={fail} stats={llm.stats}", flush=True)
    return 1 if fail else 0


if __name__ == "__main__":
    raise SystemExit(main())
