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

"""企业级 NL2SQL 评测数据集生成器。

用法（在 tools/dataset-gen 目录下）：
    python -m pip install --user -r requirements.txt
    python gen.py --config config/m0.yaml                    # 全流程
    python gen.py --config config/m0.yaml --steps validate   # 只跑指定步骤

步骤按顺序执行：core -> core_ext -> layers -> noise -> snapshot -> degrade -> validate。
snapshot 必须在 degrade 之前（保存干净元数据）。设计见 docs/superpowers/specs/2026-10-13-enterprise-dataset-design.md。
"""
import argparse
import os
import sys
import time
from pathlib import Path

import yaml

from gen import core_data, core_ext, db, degrade, layer_derive, layer_rules, noise, noise_v2, validate

STEPS = {
    "core": core_data.run,
    "core_ext": core_ext.run,
    "layers": lambda cfg, conn: (layer_derive.run(cfg, conn), layer_rules.run(cfg, conn)),
    "noise": lambda cfg, conn: (noise_v2 if cfg["noise"].get("version") == 2 else noise).run(cfg, conn),
    "snapshot": degrade.snapshot,
    "degrade": degrade.run,
    "validate": validate.run,
}


def _merge(base, override):
    """深度合并：override 中的键覆盖 base；字典递归合并，其他类型整体替换。"""
    result = dict(base)
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(result.get(key), dict):
            result[key] = _merge(result[key], value)
        else:
            result[key] = value
    return result


def load_config(path):
    """读取配置；支持 extends: 基础配置文件（相对当前文件），用于让三个档位共享完全相同的核心配置。"""
    cfg = yaml.safe_load(path.read_text(encoding="utf-8"))
    parent = cfg.pop("extends", None)
    if parent:
        cfg = _merge(load_config(path.parent / parent), cfg)
    return cfg


def prepare_database(cfg):
    name = cfg["database"]
    conn = db.connect(cfg, use_database=False)
    try:
        if cfg.get("recreate"):
            db.execute(conn, f"DROP DATABASE IF EXISTS `{name}`")
        db.execute(conn, f"CREATE DATABASE IF NOT EXISTS `{name}` DEFAULT CHARACTER SET utf8mb4 "
                         "COLLATE utf8mb4_0900_ai_ci")
    finally:
        conn.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", required=True)
    parser.add_argument("--steps", help=f"逗号分隔，可选：{','.join(STEPS)}；默认全部")
    args = parser.parse_args()

    os.chdir(Path(__file__).resolve().parent)  # 配置中的相对路径以生成器目录为基准
    cfg = load_config(Path(args.config))
    steps = args.steps.split(",") if args.steps else list(STEPS)
    unknown = set(steps) - set(STEPS)
    if unknown:
        parser.error(f"未知步骤：{unknown}")

    if "core" in steps:
        prepare_database(cfg)
    conn = db.connect(cfg)
    exit_code = 0
    timings = {}
    try:
        for name in STEPS:
            if name not in steps:
                continue
            print(f"[{name}]")
            t0 = time.perf_counter()
            result = STEPS[name](cfg, conn)
            timings[name] = round(time.perf_counter() - t0, 1)
            if name == "validate":
                exit_code = result
    finally:
        conn.close()
    print("timings(s):", timings, "total:", round(sum(timings.values()), 1))
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
