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

"""一键构建评测数据集：生成题集 → 依次生成并校验 M0 和 S2 三个档位。任何一步失败立即停止，退出码非 0。

用法（tools/dataset-gen 目录）：
    python build_all.py                      # 全部：m0 + small + medium + large
    python build_all.py m0 s2-small          # 只构建指定档位
    python build_all.py --report             # 构建后生成统计报告（需要 requirements-report.txt）

每个档位写入各自的数据库（enterprise_dw / enterprise_dw_s / _m / _l），会先 DROP 再重建。
"""
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
TIERS = ["m0", "s2-small", "s2-medium", "s2-large"]


def run(cmd):
    print(f"\n$ {' '.join(cmd)}", flush=True)
    start = time.perf_counter()
    code = subprocess.call(cmd, cwd=HERE)
    print(f"  -> exit {code}, {time.perf_counter() - start:.0f}s", flush=True)
    if code != 0:
        sys.exit(code)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    unknown = set(args) - set(TIERS)
    if unknown:
        sys.exit(f"unknown tier(s): {sorted(unknown)}; choose from {TIERS}")
    tiers = args or TIERS
    run([sys.executable, "questions/build_s2.py"])
    for tier in tiers:
        run([sys.executable, "gen.py", "--config", f"config/{tier}.yaml"])
    if "--report" in sys.argv:
        run([sys.executable, "report_s2.py"])
    print("\nall done:", ", ".join(tiers))


if __name__ == "__main__":
    main()
