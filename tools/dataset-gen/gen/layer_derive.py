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

"""数仓分层派生（ADR-003：下游一律 INSERT...SELECT 从上游计算）。

- ODS：规则生成，每张核心表 -> ods_{t}_di，增加 etl_time、dt 两列（固定值，保证可复现）
- DWD/DWS/ADS：M0 阶段由 ddl/layers/*.sql 手写；M3 再通用化
"""
from . import db


def build_ods(cfg, conn):
    etl_date = cfg["layers"]["etl_date"]
    for t in cfg["layers"]["ods_tables"]:
        ods = f"ods_{t}_di"
        comment = db.scalar(conn, "SELECT TABLE_COMMENT FROM information_schema.TABLES "
                                  "WHERE TABLE_SCHEMA=%s AND TABLE_NAME=%s", (cfg["database"], t)) or ""
        db.execute(conn, f"DROP TABLE IF EXISTS `{ods}`")
        db.execute(conn, f"CREATE TABLE `{ods}` LIKE `{t}`")  # LIKE 不复制外键，符合数仓无物理外键的现实
        db.execute(conn, f"ALTER TABLE `{ods}` "
                         "ADD COLUMN etl_time DATETIME NOT NULL COMMENT 'ETL 同步时间', "
                         "ADD COLUMN dt DATE NOT NULL COMMENT '分区日期', "
                         f"COMMENT = %s", (f"ODS-{comment}",))
        db.execute(conn, f"INSERT INTO `{ods}` SELECT s.*, %s, %s FROM `{t}` s",
                   (f"{etl_date} 02:00:00", etl_date))
        print(f"  {ods}")


def run(cfg, conn):
    build_ods(cfg, conn)
    for path in cfg["layers"]["sql_files"]:
        db.run_sql_file(conn, path)
        print(f"  {path}")
