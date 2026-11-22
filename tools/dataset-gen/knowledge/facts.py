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

"""从 large 档的元数据、分层规则和数据库里取出写知识用的事实。

这里只产出结构化的事实（表、字段、血缘 SQL、噪声表家族、枚举值、统计数字），不产出任何正文。
"""
import json
import re
from collections import defaultdict
from pathlib import Path

import pymysql

from gen import db as gendb
from gen import layer_rules

ROOT = Path(__file__).resolve().parent.parent
LAYER_PREFIXES = ("ods_", "dim_", "dwd_", "dws_", "ads_")
NOISE_SUFFIX = re.compile(r"^(bak_\d{8}|bak|\d{8}|\d{6}|v\d+|his|old)$")
NOISE_KIND = [
    (re.compile(r"^\d{6}$"), "月分表"), (re.compile(r"^\d{8}$"), "日分表"), (re.compile(r"^bak_\d{8}$"), "带日期备份"),
    (re.compile(r"^bak$"), "备份"), (re.compile(r"^v\d+$"), "旧版本"), (re.compile(r"^his$"), "历史归档"),
    (re.compile(r"^old$"), "旧版本"),
]
TABLE_TOKEN = re.compile(r"\b(?:FROM|JOIN)\s+([a-z][a-z0-9_]*)", re.I)


def layer_of(name):
    for p in LAYER_PREFIXES:
        if name.startswith(p):
            return p[:-1].upper()
    return "CORE"


class Facts:

    def __init__(self, schema="enterprise_dw_l", meta_dir=ROOT / "out" / "s2-large"):
        meta = json.loads((Path(meta_dir) / "clean_metadata.json").read_text(encoding="utf-8"))
        self.tables = meta["tables"]
        self.foreign_keys = meta["foreign_keys"]
        self.schema = schema
        self.effective = sorted(n for n, t in self.tables.items() if t["group"] in ("core", "derived"))
        self.noise = sorted(n for n, t in self.tables.items() if t["group"] == "noise")
        self.sql = self._lineage_sql()
        self.upstream = {t: self._parse_upstream(t) for t in self.effective}
        self.downstream = defaultdict(list)
        for t, ups in self.upstream.items():
            for u in ups:
                self.downstream[u].append(t)
        self.families, self.orphan_noise = self._noise_families()
        self._conn = None

    # ---------- 表与血缘 ----------

    def columns(self, table):
        return [(c, v["type"], v["comment"]) for c, v in self.tables[table]["columns"].items()]

    def comment(self, table):
        return self.tables[table]["comment"]

    def _lineage_sql(self):
        sql = {}
        for name, _, _, insert in layer_rules.FIXED_TABLES:
            sql[name] = insert.strip()
        for name, _, _, insert, _ in layer_rules.ADS_TABLES:
            sql[name] = insert.strip()
        event_keys = {v[1]: k for k, v in layer_rules.EVENTS.items()}
        for name in self.effective:
            m = re.match(r"^dws_([a-z]+)_([a-z0-9]+)_([a-z]+)_(1d|1w|1m)$", name)
            if m and m.group(3) in event_keys and m.group(2) in layer_rules.GRAINS:
                rows = layer_rules._dws_tables({"dws": {event_keys[m.group(3)]: [m.group(2)]},
                                                "periods": [m.group(4)]})
                if rows and rows[0][0] == name:
                    sql[name] = rows[0][3]
        for path in sorted((ROOT / "ddl" / "layers").glob("*.sql")):
            for stmt in gendb.split_statements(path.read_text(encoding="utf-8")):
                m = re.match(r"\s*INSERT\s+INTO\s+([a-z0-9_]+)", stmt, re.I)
                if m and m.group(1) in self.tables:
                    sql.setdefault(m.group(1), stmt.strip())
        for name in self.effective:
            if name.startswith("ods_") and name not in sql:
                core = name[4:-3]
                sql[name] = f"INSERT INTO {name} SELECT {core}.*, <固定 etl_time>, <分区 dt> FROM {core}"
        return sql

    def _parse_upstream(self, table):
        if table not in self.sql:
            return []
        found = []
        for t in TABLE_TOKEN.findall(self.sql[table]):
            if t in self.tables and t != table and t not in found:
                found.append(t)
        return found

    # ---------- 噪声表家族 ----------

    def _noise_families(self):
        families = defaultdict(lambda: defaultdict(list))
        orphan = []
        by_len = sorted(self.effective, key=len, reverse=True)
        for n in self.noise:
            owner, kind = None, None
            if n.startswith("test_") and n[5:] in self.tables and self.tables[n[5:]]["group"] != "noise":
                owner, kind = n[5:], "测试表"
            else:
                for e in by_len:
                    if n.startswith(e + "_") and NOISE_SUFFIX.match(n[len(e) + 1:]):
                        owner = e
                        suffix = n[len(e) + 1:]
                        kind = next(k for p, k in NOISE_KIND if p.match(suffix))
                        break
            if owner:
                families[owner][kind].append(n)
            else:
                orphan.append(n)
        return families, orphan

    def family_summary(self, table, examples=4):
        fam = self.families.get(table, {})
        out = []
        for kind, names in sorted(fam.items()):
            names = sorted(names)
            item = {"类型": kind, "张数": len(names), "示例": names[:examples]}
            if kind in ("月分表", "日分表"):
                item["范围"] = f"{names[0]} ～ {names[-1]}"
            out.append(item)
        return out

    def orphan_noise_summary(self):
        groups = defaultdict(list)
        for n in self.orphan_noise:
            if n.startswith("tmp_"):
                groups["临时表 tmp_*"].append(n)
            elif n.startswith("u_"):
                groups["个人副本 u_<用户>_*"].append(n)
            else:
                groups["其他"].append(n)
        return {k: {"张数": len(v), "示例": sorted(v)[:6]} for k, v in groups.items()}

    # ---------- 数据库 ----------

    def conn(self):
        if self._conn is None:
            self._conn = pymysql.connect(host="127.0.0.1", port=3306, user="root", password="root",
                                         database=self.schema, charset="utf8mb4", autocommit=True)
        return self._conn

    def query(self, sql, args=None):
        with self.conn().cursor() as cur:
            cur.execute(sql, args)
            return cur.fetchall()

    def row_count(self, table):
        return self.query(f"SELECT COUNT(*) FROM `{table}`")[0][0]

    def enums(self, max_values=12):
        """核心表里取值少的列（状态、渠道、类型等）：[(表, 列, 注释, [(值, 行数)])]。"""
        result = []
        for t in self.effective:
            if layer_of(t) != "CORE":
                continue
            for c, typ, cm in self.columns(t):
                if not re.match(r"^(varchar\((\d|1\d|20)\)|char\(\d+\)|tinyint)", typ) or c.endswith("_id"):
                    continue
                rows = self.query(f"SELECT `{c}`, COUNT(*) FROM `{t}` GROUP BY `{c}` ORDER BY COUNT(*) DESC LIMIT 20")
                if 2 <= len(rows) <= max_values:
                    result.append((t, c, cm, [(str(v), n) for v, n in rows]))
        return result
