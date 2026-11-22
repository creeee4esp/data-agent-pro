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

"""元数据快照与退化。

顺序：先 snapshot（保存干净元数据），再 degrade。退化规则：
- 按表分组（core/derived/noise）删除表注释、列注释
- 核心表物理外键按比例删除（派生层与噪声表本来就没有外键）
- 少量金额列注释误标"单位：分"（实际存元）
- 列名缩写只作用于 noise 表：核心表的不规范命名已直接写在 DDL 里，避免改名导致题集标准 SQL 失效（ADR-005）
"""
import json
import random
from pathlib import Path

from . import db

ABBREVIATIONS = {"amount": "amt", "quantity": "qty", "price": "prc", "category": "cate", "status": "stat",
                 "time": "tm", "name": "nm", "province": "prov", "order": "ord", "user": "usr"}


def snapshot(cfg, conn):
    noise = db.noise_tables(cfg)
    database = cfg["database"]
    tables = {}
    for name, comment in db.execute(conn, "SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES "
                                          "WHERE TABLE_SCHEMA=%s AND TABLE_TYPE='BASE TABLE'", (database,)):
        tables[name] = {"group": db.table_group(name, cfg["core_tables"], noise), "comment": comment, "columns": {}}
    for t, c, ctype, comment in db.execute(
            conn, "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT FROM information_schema.COLUMNS "
                  "WHERE TABLE_SCHEMA=%s ORDER BY TABLE_NAME, ORDINAL_POSITION", (database,)):
        if t in tables:
            tables[t]["columns"][c] = {"type": ctype, "comment": comment}
    fks = [dict(zip(("name", "table", "column", "ref_table", "ref_column"), r)) for r in db.execute(
        conn, "SELECT CONSTRAINT_NAME, TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
              "FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=%s AND REFERENCED_TABLE_NAME IS NOT NULL "
              "ORDER BY TABLE_NAME, CONSTRAINT_NAME", (database,))]
    out = Path(cfg["out_dir"]) / "clean_metadata.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"tables": tables, "foreign_keys": fks}, ensure_ascii=False, indent=1),
                   encoding="utf-8")
    print(f"  snapshot: {len(tables)} tables, {len(fks)} fks -> {out}")


def _abbreviate(col, rng, ratio):
    parts = col.split("_")
    hits = [i for i, p in enumerate(parts) if p in ABBREVIATIONS]
    if not hits or rng.random() >= ratio:
        return None
    i = rng.choice(hits)
    parts[i] = ABBREVIATIONS[parts[i]]
    return "_".join(parts)


def run(cfg, conn):
    rng = random.Random(cfg["seed"] + 2)
    noise = db.noise_tables(cfg)
    d = cfg["degrade"]
    database = cfg["database"]
    log = {"table_comment_dropped": 0, "column_comment_dropped": 0, "wrong_comment": 0,
           "fk_dropped": 0, "column_renamed": 0, "renames": []}

    # S2：每张表用独立的随机数生成器（种子 = 全局种子 + 表名），某张表的退化结果不受其他表是否存在的影响，
    # 三个档位里同一张核心表的元数据退化完全相同（控制变量）。M0 保持原来的单一序列，结果不变。
    per_table = d.get("per_table_rng", False)
    for t in db.list_tables(conn, database):
        group = db.table_group(t, cfg["core_tables"], noise)
        if per_table:
            rng = random.Random(f"{cfg['seed']}:degrade:{t}")
        if rng.random() < d["drop_table_comment"][group]:
            db.execute(conn, f"ALTER TABLE `{t}` COMMENT = ''")
            log["table_comment_dropped"] += 1

        defs = db.column_definitions(conn, t)
        comments = dict(db.execute(conn, "SELECT COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS "
                                         "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s", (database, t)))
        changes = []
        for col, definition in defs.items():
            comment = comments.get(col, "")
            if not comment:
                continue
            if rng.random() < d["drop_column_comment"][group]:
                changes.append(f"MODIFY COLUMN {definition}")
                log["column_comment_dropped"] += 1
            elif "金额" in comment and "decimal" in definition.lower() and rng.random() < d["wrong_comment"]:
                wrong = comment + "（单位：分）"
                changes.append(f"MODIFY COLUMN {definition} COMMENT '{wrong.replace(chr(39), chr(39) * 2)}'")
                log["wrong_comment"] += 1
        if changes:
            db.execute(conn, f"ALTER TABLE `{t}` " + ", ".join(changes))
        if group == "noise":  # 改名单独一条 ALTER，避免与同列 MODIFY 冲突
            renames = []
            for col in defs:
                new = _abbreviate(col, rng, d["abbreviate_column"])
                if new and new not in defs:
                    renames.append(f"RENAME COLUMN `{col}` TO `{new}`")
                    log["renames"].append(f"{t}.{col}->{new}")
            if renames:
                db.execute(conn, f"ALTER TABLE `{t}` " + ", ".join(renames))
                log["column_renamed"] += len(renames)

    for name, table in db.execute(conn, "SELECT CONSTRAINT_NAME, TABLE_NAME FROM information_schema.TABLE_CONSTRAINTS "
                                        "WHERE TABLE_SCHEMA=%s AND CONSTRAINT_TYPE='FOREIGN KEY' "
                                        "ORDER BY TABLE_NAME, CONSTRAINT_NAME", (database,)):
        if per_table:
            rng = random.Random(f"{cfg['seed']}:fk:{name}")
        if db.table_group(table, cfg["core_tables"], noise) == "core" and rng.random() < d["drop_core_fk"]:
            db.execute(conn, f"ALTER TABLE `{table}` DROP FOREIGN KEY `{name}`")
            log["fk_dropped"] += 1

    out = Path(cfg["out_dir"]) / "degrade_log.json"
    out.write_text(json.dumps(log, ensure_ascii=False, indent=1), encoding="utf-8")
    print("  " + ", ".join(f"{k}={v}" for k, v in log.items() if k != "renames"))
