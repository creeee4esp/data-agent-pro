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

"""OpenAI 兼容的对话接口：磁盘缓存（同样的请求只调用一次，重跑可复现）+ 重试。

只用标准库。API key 从环境变量 BENCHMARK_CHAT_API_KEY 读取（与 Java 评测器一致）。
"""
import hashlib
import json
import os
import random
import threading
import time
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_MODEL = "deepseek-flash"


class FatalLlmError(RuntimeError):
    """余额不足、鉴权失败等重试也没用的错误：调用方应停止整批任务。"""


class LlmClient:

    def __init__(self, cache_dir, base_url=DEFAULT_BASE_URL, model=DEFAULT_MODEL, use_env_proxy=False,
                 cache_only=False):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.cache_dir = Path(cache_dir) / model
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.cache_only = cache_only
        self.api_key = os.environ.get("BENCHMARK_CHAT_API_KEY", "")
        handlers = [] if use_env_proxy else [urllib.request.ProxyHandler({})]
        self.opener = urllib.request.build_opener(*handlers)
        self.lock = threading.Lock()
        self.stats = {"calls": 0, "hits": 0, "prompt_tokens": 0, "completion_tokens": 0}

    def chat(self, messages, max_tokens=8000, temperature=0.7):
        body = {"model": self.model, "messages": messages, "max_tokens": max_tokens, "temperature": temperature}
        key = hashlib.sha256(json.dumps(body, ensure_ascii=False, sort_keys=True).encode("utf-8")).hexdigest()
        path = self.cache_dir / f"{key}.json"
        if path.exists():
            with self.lock:
                self.stats["hits"] += 1
            return json.loads(path.read_text(encoding="utf-8"))["content"]
        if self.cache_only:
            raise RuntimeError("cache miss in cache-only mode")
        if not self.api_key:
            raise RuntimeError("BENCHMARK_CHAT_API_KEY is not set")
        content, usage = self._post(body)
        if content.strip():
            # 原子写：先写临时文件再改名，并发时不会读到半个文件
            tmp = path.with_suffix(f".{threading.get_ident()}.tmp")
            tmp.write_text(json.dumps({"content": content, "usage": usage}, ensure_ascii=False), encoding="utf-8")
            os.replace(tmp, path)
        with self.lock:
            self.stats["calls"] += 1
            self.stats["prompt_tokens"] += usage.get("prompt_tokens", 0)
            self.stats["completion_tokens"] += usage.get("completion_tokens", 0)
        return content

    def _post(self, body):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        last = None
        for attempt in range(6):
            req = urllib.request.Request(self.base_url + "/chat/completions", data=data, headers={
                "Content-Type": "application/json", "Authorization": "Bearer " + self.api_key})
            try:
                with self.opener.open(req, timeout=300) as resp:
                    result = json.loads(resp.read().decode("utf-8"))
                return result["choices"][0]["message"].get("content") or "", result.get("usage", {})
            except urllib.error.HTTPError as e:
                last = e
                if e.code in (401, 402, 403):
                    raise FatalLlmError(f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:300]}") from e
                if e.code not in (429, 500, 502, 503, 504):
                    raise RuntimeError(f"HTTP {e.code}: {e.read().decode('utf-8', 'replace')[:300]}") from e
            except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
                last = e
            time.sleep(min(60, 2 ** attempt + random.random()))
        raise RuntimeError(f"LLM request failed after retries: {last}")
