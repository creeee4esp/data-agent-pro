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

"""本地 reranker 服务：支持 decoder 式（Qwen3-Reranker）与 encoder 式（bge-reranker）模型。

接口（与 TEI / Jina 的 rerank 接口形状一致）：
    POST /rerank  {"query": "...", "texts": ["...", ...]}
    -> [{"index": 0, "score": 0.93}, ...]   按分数降序

两类模型的打分方式不同：

- decoder 式（Qwen3-Reranker-0.6B 等）：把（指令, 问题, 文档）拼成对话，读取下一个 token 是 "yes" 还是
  "no" 的概率，score = P(yes) / (P(yes) + P(no))。写法按模型卡的官方示例。
- encoder 式（BAAI/bge-reranker-base 等）：问题与文档拼成一个序列做一次前向，取分类头 logit 的 sigmoid。

运行：
    tools/reranker/.venv/Scripts/python tools/reranker/server.py --model BAAI/bge-reranker-base --max-chars 300
"""
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock

import torch
from transformers import (AutoConfig, AutoModelForCausalLM, AutoModelForSequenceClassification,
                          AutoTokenizer)

INSTRUCTION = "Given a data analysis question, judge whether the knowledge passage helps answer it"
PREFIX = ("<|im_start|>system\nJudge whether the Document meets the requirements based on the Query and the "
          "Instruct provided. Note that the answer can only be \"yes\" or \"no\".<|im_end|>\n<|im_start|>user\n")
SUFFIX = "<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n"

parser = argparse.ArgumentParser()
parser.add_argument("--model", default="Qwen/Qwen3-Reranker-0.6B", help="HF 模型名或本地路径")
parser.add_argument("--device", default="cpu", help="cpu / cuda")
parser.add_argument("--port", type=int, default=8085)
parser.add_argument("--max-chars", type=int, default=1200, help="每段文档截断到多少字再打分")
parser.add_argument("--max-length", type=int, default=512, help="encoder 模型的最大 token 数")
parser.add_argument("--batch", type=int, default=8)
parser.add_argument("--threads", type=int, default=0, help="torch CPU 线程数，0 表示用默认值")
args = parser.parse_args()

if args.threads > 0:
    torch.set_num_threads(args.threads)

config = AutoConfig.from_pretrained(args.model)
ENCODER = any("SequenceClassification" in (architecture or "") for architecture in (config.architectures or []))

tokenizer = AutoTokenizer.from_pretrained(args.model)
if ENCODER:
    model = AutoModelForSequenceClassification.from_pretrained(args.model, dtype=torch.float32).eval().to(args.device)
else:
    tokenizer.padding_side = "left"
    model = AutoModelForCausalLM.from_pretrained(args.model, dtype=torch.float32).eval().to(args.device)
    YES = tokenizer.convert_tokens_to_ids("yes")
    NO = tokenizer.convert_tokens_to_ids("no")

lock = Lock()


def score(query, texts):
    out = []
    with torch.no_grad():
        for i in range(0, len(texts), args.batch):
            chunk = [t[:args.max_chars] for t in texts[i:i + args.batch]]
            if ENCODER:
                batch = tokenizer([query] * len(chunk), chunk, padding=True, truncation="only_second",
                                  max_length=args.max_length, return_tensors="pt").to(args.device)
                out.extend(torch.sigmoid(model(**batch).logits.view(-1)).tolist())
            else:
                pairs = [f"{PREFIX}<Instruct>: {INSTRUCTION}\n<Query>: {query}\n<Document>: {t}{SUFFIX}"
                         for t in chunk]
                batch = tokenizer(pairs, padding=True, truncation=True, max_length=2048,
                                  return_tensors="pt").to(args.device)
                logits = model(**batch).logits[:, -1, :]
                two = torch.stack([logits[:, NO], logits[:, YES]], dim=1)
                out.extend(torch.softmax(two, dim=1)[:, 1].tolist())
    return out


class Handler(BaseHTTPRequestHandler):

    def do_POST(self):
        if self.path != "/rerank":
            self.send_error(404)
            return
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        start = time.time()
        with lock:
            scores = score(body["query"], body.get("texts") or [])
        result = sorted(({"index": i, "score": s} for i, s in enumerate(scores)), key=lambda r: -r["score"])
        data = json.dumps(result).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)
        print(f"rerank {len(scores)} texts in {time.time() - start:.2f}s", flush=True)

    def log_message(self, *a):
        pass


print(f"{args.model} ready on 127.0.0.1:{args.port} (encoder={ENCODER}, device={args.device})", flush=True)
ThreadingHTTPServer(("127.0.0.1", args.port), Handler).serve_forever()
