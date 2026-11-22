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

"""S2 噪声表生成（v2）：先确定性地规划出全部噪声表，再用多个连接并行建表。

与 M0 的 noise.py 相比：
- 噪声源从核心表扩展到"核心表 + 派生表"（按配置的前缀筛选）；
- 新增按月（多年）、按日分表；
- 复制取样一律 ORDER BY 主键（没有主键则按全部列），保证取到的行确定（IMPLEMENTATION.md 10.5）；
- 规划阶段只用一个随机数生成器、按表名顺序进行，执行顺序不影响结果，所以可以并行。
"""
import datetime as dt
import json
import random
import threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from . import db, dist


def _columns(conn, database, table):
    rows = db.execute(conn, "SELECT COLUMN_NAME, COLUMN_KEY FROM information_schema.COLUMNS "
                            "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION", (database, table))
    return [r[0] for r in rows], [r[0] for r in rows if r[1] == "PRI"]


def _order_by(cols, pk):
    return ", ".join(f"`{c}`" for c in (pk or cols))


def _short(table):
    """表名的后两段，用于临时表和个人副本命名：trade_order_item -> order_item。"""
    parts = table.split("_")
    return "_".join(parts[-2:]) if len(parts) >= 2 else table


def plan(cfg, conn):
    """返回 [(表名, SQL 列表)]。只读数据库元数据，不建表。"""
    n = cfg["noise"]
    database = cfg["database"]
    rng = random.Random(cfg["seed"] + 101)
    year = cfg["scale"]["year"]
    all_tables = db.list_tables(conn, database)
    prefixes = tuple(n["source_prefixes"])
    sources = [t for t in all_tables if t in cfg["core_tables"] or t.startswith(prefixes)]
    meta = {t: _columns(conn, database, t) for t in sources}
    copy_rows = int(n["copy_rows"])
    planned = {}

    def rand_date():
        return dt.date(year, 1, 1) + dt.timedelta(days=rng.randrange(365))

    def like_copy(src, dst, rows, where=""):
        cols, pk = meta[src]
        sqls = [f"CREATE TABLE `{dst}` LIKE `{src}`"]
        if rows:
            sqls.append(f"INSERT INTO `{dst}` SELECT * FROM `{src}` {where} ORDER BY {_order_by(cols, pk)} LIMIT {rows}")
        return sqls

    def ctas(src, dst, keep, rows):
        cols, pk = meta[src]
        col_sql = ", ".join(f"`{c}`" for c in keep)
        return [f"CREATE TABLE `{dst}` AS SELECT {col_sql} FROM `{src}` ORDER BY {_order_by(cols, pk)} LIMIT {rows}"]

    for t in sources:
        cols, pk = meta[t]
        non_pk = [c for c in cols if c not in pk]
        if rng.random() < n["backup_ratio"]:
            planned.setdefault(f"{t}_bak", like_copy(t, f"{t}_bak", copy_rows))
        for _ in range(rng.randint(0, n["dated_backups_max"])):
            name = f"{t}_bak_{rand_date():%Y%m%d}"
            planned.setdefault(name, like_copy(t, name, copy_rows))
        if rng.random() < n["tmp_ratio"]:
            name = f"tmp_{_short(t)}_{rand_date():%m%d}"
            keep = cols[: max(2, len(cols) - rng.randint(1, 3))]
            planned.setdefault(name, ctas(t, name, keep, copy_rows * 2))
        if rng.random() < n["old_version_ratio"] and len(non_pk) > 2:
            name = f"{t}_{rng.choice(['v1', 'v2', 'old', 'his'])}"
            dropped = rng.sample(non_pk, rng.randint(1, 2))
            remain = ", ".join(f"`{c}`" for c in cols if c not in dropped)
            planned.setdefault(name, [f"CREATE TABLE `{name}` LIKE `{t}`",
                                      f"ALTER TABLE `{name}` " + ", ".join(f"DROP COLUMN `{c}`" for c in dropped),
                                      f"INSERT INTO `{name}` ({remain}) SELECT {remain} FROM `{t}` "
                                      f"ORDER BY {_order_by(cols, pk)} LIMIT {copy_rows}"])
        if rng.random() < n["test_ratio"]:
            planned.setdefault(f"test_{t}", like_copy(t, f"test_{t}", 0))

    for _ in range(n["personal_copies"]):
        t = rng.choice(sources)
        cols, _ = meta[t]
        keep = sorted(rng.sample(cols, min(len(cols), rng.randint(3, 5))), key=cols.index)
        name = f"u_{rng.choice(dist.PINYIN_NAMES)}_{_short(t)}_{rng.choice(['analysis', 'tmp', 'check', 'export'])}"
        planned.setdefault(name, ctas(t, name, keep, copy_rows // 2))

    # 按月分表：多年；数据只有 config 年份那一年，其他年份为空表（历史归档表）
    for t, time_col in (n.get("monthly_shards") or {}).items():
        for y in n.get("shard_years", [year]):
            for m in range(1, 13):
                name = f"{t}_{y}{m:02d}"
                where = f"WHERE YEAR(`{time_col}`) = {y} AND MONTH(`{time_col}`) = {m}"
                planned.setdefault(name, like_copy(t, name, n["shard_rows"] if y == year else 0, where))

    # 按日分表：数据年份的每一天
    for t, time_col in (n.get("daily_shards") or {}).items():
        day = dt.date(year, 1, 1)
        while day.year == year:
            name = f"{t}_{day:%Y%m%d}"
            planned.setdefault(name, like_copy(t, name, n["daily_shard_rows"], f"WHERE DATE(`{time_col}`) = '{day}'"))
            day += dt.timedelta(days=1)

    return sorted(planned.items())


def run(cfg, conn):
    jobs = plan(cfg, conn)
    workers = int(cfg["noise"].get("parallelism", 4))
    local = threading.local()
    connections = []
    lock = threading.Lock()

    def worker_conn():
        if not hasattr(local, "conn"):
            local.conn = db.connect(cfg)
            with lock:
                connections.append(local.conn)
        return local.conn

    def build(job):
        name, sqls = job
        c = worker_conn()
        db.execute(c, f"DROP TABLE IF EXISTS `{name}`")
        for sql in sqls:
            db.execute(c, sql)

    try:
        with ThreadPoolExecutor(max_workers=workers) as pool:
            list(pool.map(build, jobs))  # list() 让异常在这里抛出
    finally:
        for c in connections:
            c.close()
    names = [name for name, _ in jobs]
    out = Path(cfg["out_dir"]) / "noise_tables.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(names, ensure_ascii=False), encoding="utf-8")
    print(f"  {len(jobs)} noise tables (parallelism={workers})")
    return names
