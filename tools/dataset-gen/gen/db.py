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

"""数据库连接与通用 SQL 工具。"""
import os
import re
from pathlib import Path

import pymysql


def connect(cfg, use_database=True):
    db = cfg["db"]
    return pymysql.connect(
        host=db["host"],
        port=int(db["port"]),
        user=db["user"],
        password=os.environ.get("DATASET_DB_PASSWORD", db["password"]),
        database=cfg["database"] if use_database else None,
        charset="utf8mb4",
        local_infile=True,
        autocommit=True,
    )


def execute(conn, sql, args=None):
    with conn.cursor() as cur:
        cur.execute(sql, args)
        return cur.fetchall()


def scalar(conn, sql, args=None):
    rows = execute(conn, sql, args)
    return rows[0][0] if rows else None


def split_statements(text):
    """按行尾分号切分 SQL；忽略整行 -- 注释。DDL 中不在字符串里使用半角分号。"""
    statements, buf = [], []
    for line in text.splitlines():
        if line.strip().startswith("--") or not line.strip():
            continue
        buf.append(line)
        if line.rstrip().endswith(";"):
            statements.append("\n".join(buf).rstrip().rstrip(";"))
            buf = []
    if buf and "".join(buf).strip():
        statements.append("\n".join(buf))
    return statements


def run_sql_file(conn, path):
    text = Path(path).read_text(encoding="utf-8")
    for stmt in split_statements(text):
        execute(conn, stmt)


def load_csv(conn, table, csv_path, columns):
    """LOAD DATA LOCAL INFILE 批量导入；CSV 中 \\N 表示 NULL。"""
    path = Path(csv_path).resolve().as_posix().replace("'", "\\'")
    cols = ", ".join(f"`{c}`" for c in columns)
    execute(conn, "SET SESSION foreign_key_checks = 0, unique_checks = 0")
    try:
        execute(
            conn,
            f"LOAD DATA LOCAL INFILE '{path}' INTO TABLE `{table}` CHARACTER SET utf8mb4 "
            "FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '\"' "
            f"LINES TERMINATED BY '\\n' IGNORE 1 LINES ({cols})",
        )
    finally:
        execute(conn, "SET SESSION foreign_key_checks = 1, unique_checks = 1")


def list_tables(conn, database):
    rows = execute(
        conn,
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s AND TABLE_TYPE='BASE TABLE' "
        "ORDER BY TABLE_NAME",
        (database,),
    )
    return [r[0] for r in rows]


DERIVED_PREFIXES = ("ods_", "dwd_", "dws_", "ads_", "dim_")


def table_group(name, core_tables, noise_tables=frozenset()):
    """表分组：core / derived / noise。噪声表优先按 noise_tables 清单判断（S2 中噪声表可能以 dws_ 等派生前缀开头）。"""
    if name in core_tables:
        return "core"
    if name in noise_tables:
        return "noise"
    if name.startswith(DERIVED_PREFIXES):
        return "derived"
    return "noise"


_COMMENT_RE = re.compile(r"\s+COMMENT\s+'(?:[^'\\]|\\.|'')*'")


def column_definitions(conn, table):
    """从 SHOW CREATE TABLE 解析列定义行：{列名: 不含 COMMENT 的完整定义}。"""
    ddl = execute(conn, f"SHOW CREATE TABLE `{table}`")[0][1]
    defs = {}
    for line in ddl.splitlines():
        m = re.match(r"\s+`([^`]+)`\s", line)
        if not m:
            continue
        body = line.strip().rstrip(",")
        defs[m.group(1)] = _COMMENT_RE.sub("", body)
    return defs


def noise_tables(cfg):
    """读取 noise_v2 写出的噪声表清单；M0（noise v1）没有这个文件，返回空集合，分组规则不变。"""
    import json
    path = Path(cfg["out_dir"]) / "noise_tables.json"
    if not path.exists():
        return frozenset()
    return frozenset(json.loads(path.read_text(encoding="utf-8")))
