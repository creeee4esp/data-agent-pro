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

"""对本地 reranker 服务做一次简单吞吐测量（只用于冒烟/选型，不是评测）。

用法：
    tools/reranker/.venv/Scripts/python tools/reranker/bench_local.py --docs 16 --chars 300
"""
import argparse
import json
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("--url", default="http://127.0.0.1:8085/rerank")
parser.add_argument("--query", default="上周华东区的销售周报里，退货率是多少？")
parser.add_argument("--docs", type=int, default=16)
parser.add_argument("--chars", type=int, default=300)
parser.add_argument("--rounds", type=int, default=2)
args = parser.parse_args()

BASE = ("华东区销售周报（第{n}周）：本周华东区销售额 1,280 万元，退货率 2.3%，环比下降 0.4 个百分点；"
        "重点客户回款率 91%，新增订单 236 笔，客单价 5.4 万元；库存周转天数 18 天，缺货 SKU 12 个。")
texts = []
for i in range(args.docs):
    text = BASE.format(n=40 + i)
    texts.append((text * (args.chars // len(text) + 1))[:args.chars])

body = json.dumps({"query": args.query, "texts": texts}, ensure_ascii=False).encode("utf-8")
for r in range(args.rounds):
    start = time.time()
    req = urllib.request.Request(args.url, data=body, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=600) as resp:
        result = json.loads(resp.read().decode("utf-8"))
    elapsed = time.time() - start
    print(f"round {r + 1}: {len(result)} texts in {elapsed:.2f}s -> {len(result) / elapsed:.2f} texts/s")
