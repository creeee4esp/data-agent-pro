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

"""噪声表：备份、带日期备份、临时、旧版本、测试、个人副本、按月分表。

全部由规则从源表复制而来；同一种子下表名与内容确定。
"""
import datetime as dt
import random

from . import db, dist


def _columns(conn, database, table):
    rows = db.execute(conn, "SELECT COLUMN_NAME, COLUMN_KEY FROM information_schema.COLUMNS "
                            "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s ORDER BY ORDINAL_POSITION",
                      (database, table))
    return [(r[0], r[1] == "PRI") for r in rows]


def _copy_like(conn, src, dst, limit):
    db.execute(conn, f"DROP TABLE IF EXISTS `{dst}`")
    db.execute(conn, f"CREATE TABLE `{dst}` LIKE `{src}`")
    if limit:
        db.execute(conn, f"INSERT INTO `{dst}` SELECT * FROM `{src}` LIMIT {int(limit)}")


def _ctas(conn, src, dst, cols, limit):
    col_sql = ", ".join(f"`{c}`" for c in cols)
    db.execute(conn, f"DROP TABLE IF EXISTS `{dst}`")
    db.execute(conn, f"CREATE TABLE `{dst}` AS SELECT {col_sql} FROM `{src}` LIMIT {int(limit)}")


def run(cfg, conn):
    rng = random.Random(cfg["seed"] + 1)
    n = cfg["noise"]
    year = cfg["scale"]["year"]
    database = cfg["database"]
    created = []

    def rand_date():
        return dt.date(year, 1, 1) + dt.timedelta(days=rng.randrange(365))

    for t in n["source_tables"]:
        cols = _columns(conn, database, t)
        non_pk = [c for c, pk in cols if not pk]
        if rng.random() < n["backup_ratio"]:
            _copy_like(conn, t, f"{t}_bak", n["copy_rows"])
            created.append(f"{t}_bak")
        for _ in range(rng.randint(0, n["dated_backups_max"])):
            name = f"{t}_bak_{rand_date():%Y%m%d}"
            _copy_like(conn, t, name, n["copy_rows"])
            created.append(name)
        if rng.random() < n["tmp_ratio"]:
            short = t.split("_")[-1]
            name = f"tmp_{short}_{rand_date():%m%d}"
            keep = [c for c, _ in cols][: max(2, len(cols) - rng.randint(1, 3))]
            _ctas(conn, t, name, keep, n["copy_rows"] * 2)
            created.append(name)
        if rng.random() < n["old_version_ratio"] and len(non_pk) > 2:
            name = f"{t}_{rng.choice(['v1', 'old'])}"
            _copy_like(conn, t, name, 0)
            dropped = rng.sample(non_pk, rng.randint(1, 2))
            db.execute(conn, f"ALTER TABLE `{name}` " + ", ".join(f"DROP COLUMN `{c}`" for c in dropped))
            remain = ", ".join(f"`{c}`" for c, _ in cols if c not in dropped)
            db.execute(conn, f"INSERT INTO `{name}` ({remain}) SELECT {remain} FROM `{t}` LIMIT {n['copy_rows']}")
            created.append(name)
        if rng.random() < n["test_ratio"]:
            name = f"test_{t}"
            _copy_like(conn, t, name, 0)
            created.append(name)

    for _ in range(n["personal_copies"]):
        t = rng.choice(n["source_tables"])
        cols = [c for c, _ in _columns(conn, database, t)]
        keep = sorted(rng.sample(cols, min(len(cols), rng.randint(3, 5))), key=cols.index)
        name = f"u_{rng.choice(dist.PINYIN_NAMES)}_{t.split('_')[-1]}_analysis"
        _ctas(conn, t, name, keep, n["copy_rows"] // 2)
        created.append(name)

    for t, time_col in (n.get("monthly_shards") or {}).items():
        for month in range(1, 13):
            name = f"{t}_{year}{month:02d}"
            db.execute(conn, f"DROP TABLE IF EXISTS `{name}`")
            db.execute(conn, f"CREATE TABLE `{name}` LIKE `{t}`")
            db.execute(conn, f"INSERT INTO `{name}` SELECT * FROM `{t}` "
                             f"WHERE YEAR(`{time_col}`)=%s AND MONTH(`{time_col}`)=%s LIMIT {int(n['shard_rows'])}",
                       (year, month))
            created.append(name)

    created = sorted(set(created))
    print(f"  {len(created)} noise tables")
    return created
