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

"""数据集校验：一致性约束、分层一致性、注入异常、规模统计、题集可执行性。

一致性/分层/题集任一失败时返回非 0，供 gen.py 以非零退出码结束。
"""
import datetime as dt
import decimal
import json
from pathlib import Path

from . import db

PAID = "('paid','shipped','completed','refunded')"

# (名称, 返回违例行数的 SQL)
CONSISTENCY = [
    ("item_amount = qty * unit_price", "SELECT COUNT(*) FROM trade_order_item WHERE item_amount <> qty * unit_price"),
    ("unit_price = sku.price",
     "SELECT COUNT(*) FROM trade_order_item i JOIN item_sku s ON s.sku_id = i.sku_id WHERE i.unit_price <> s.price"),
    ("total_amount = sum(item_amount)",
     "SELECT COUNT(*) FROM trade_order o JOIN (SELECT order_id, SUM(item_amount) a FROM trade_order_item "
     "GROUP BY order_id) t ON t.order_id = o.order_id WHERE o.total_amount <> t.a"),
    ("pay_amount = total - discount", "SELECT COUNT(*) FROM trade_order WHERE pay_amount <> total_amount - discount_amt"),
    ("0 <= discount < total", "SELECT COUNT(*) FROM trade_order WHERE discount_amt < 0 OR discount_amt >= total_amount"),
    ("paid status <=> pay_time not null",
     f"SELECT COUNT(*) FROM trade_order WHERE (order_status IN {PAID}) <> (pay_time IS NOT NULL)"),
    ("pay_time > order_time", "SELECT COUNT(*) FROM trade_order WHERE pay_time <= order_time"),
    ("order_time >= register_time",
     "SELECT COUNT(*) FROM trade_order o JOIN user_info u ON u.user_id = o.buyer_id WHERE o.order_time < u.register_time"),
    ("order has items",
     "SELECT COUNT(*) FROM trade_order o WHERE NOT EXISTS (SELECT 1 FROM trade_order_item i WHERE i.order_id = o.order_id)"),
    ("orphan item.order_id",
     "SELECT COUNT(*) FROM trade_order_item i LEFT JOIN trade_order o ON o.order_id = i.order_id WHERE o.order_id IS NULL"),
    ("orphan item.sku_id",
     "SELECT COUNT(*) FROM trade_order_item i LEFT JOIN item_sku s ON s.sku_id = i.sku_id WHERE s.sku_id IS NULL"),
    ("orphan order.buyer_id",
     "SELECT COUNT(*) FROM trade_order o LEFT JOIN user_info u ON u.user_id = o.buyer_id WHERE u.user_id IS NULL"),
    ("orphan sku.category_id (leaf)",
     "SELECT COUNT(*) FROM item_sku s LEFT JOIN item_category c ON c.category_id = s.category_id "
     "WHERE c.category_id IS NULL OR c.cate_level <> 2"),
]

# (名称, 返回两个值的 SQL，应相等)
LAYER_CHECKS = [
    ("dwd rows = core items", "SELECT (SELECT COUNT(*) FROM dwd_trade_order_detail_di), (SELECT COUNT(*) FROM trade_order_item)"),
    ("ads total gmv = core paid items",
     "SELECT (SELECT SUM(gmv) FROM ads_trade_gmv_daily), (SELECT SUM(i.item_amount) FROM trade_order o "
     "JOIN trade_order_item i ON i.order_id = o.order_id WHERE o.pay_time IS NOT NULL)"),
    ("ads paid orders = core paid orders",
     "SELECT (SELECT SUM(paid_order_cnt) FROM ads_trade_gmv_daily), (SELECT COUNT(*) FROM trade_order WHERE pay_time IS NOT NULL)"),
]

ANOMALIES = [
    ("user_info.city IS NULL", "SELECT COUNT(*) FROM user_info WHERE city IS NULL"),
    ("item_sku.stock < 0", "SELECT COUNT(*) FROM item_sku WHERE stock < 0"),
]

# S2 扩展核心表的一致性约束（只有表存在时才检查，M0 不受影响）
CONSISTENCY_S2 = [
    ("pay_order <=> paid orders",
     "SELECT (SELECT COUNT(*) FROM trade_order WHERE pay_time IS NOT NULL) - (SELECT COUNT(*) FROM pay_order o "
     "JOIN trade_order t ON t.order_id = o.order_id WHERE o.pay_amt = t.pay_amount AND o.pay_time = t.pay_time "
     "AND o.uid = t.buyer_id)"),
    ("refund only for refunded orders",
     "SELECT COUNT(*) FROM pay_refund r JOIN trade_order o ON o.order_id = r.order_id WHERE o.order_status <> 'refunded'"),
    ("every refunded order has a refund",
     "SELECT COUNT(*) FROM trade_order o LEFT JOIN pay_refund r ON r.order_id = o.order_id "
     "WHERE o.order_status = 'refunded' AND r.order_id IS NULL"),
    ("0 < refund_amount <= pay_amount",
     "SELECT COUNT(*) FROM pay_refund r JOIN trade_order o ON o.order_id = r.order_id "
     "WHERE r.refund_amount <= 0 OR r.refund_amount > o.pay_amount"),
    ("pay_time < refund apply < finish",
     "SELECT COUNT(*) FROM pay_refund r JOIN trade_order o ON o.order_id = r.order_id "
     "WHERE r.apply_time <= o.pay_time OR r.finish_time <= r.apply_time"),
    ("shipment <=> shipped/completed/refunded",
     "SELECT (SELECT COUNT(*) FROM trade_order WHERE order_status IN ('shipped','completed','refunded')) "
     "- (SELECT COUNT(*) FROM lgs_shipment s JOIN trade_order o ON o.order_id = s.ord_no "
     "WHERE o.order_status IN ('shipped','completed','refunded'))"),
    ("pay_time < ship_time < sign_time",
     "SELECT COUNT(*) FROM lgs_shipment s JOIN trade_order o ON o.order_id = s.ord_no "
     "WHERE s.ship_time <= o.pay_time OR (s.sign_time IS NOT NULL AND s.sign_time <= s.ship_time)"),
    ("signed <=> completed/refunded",
     "SELECT COUNT(*) FROM lgs_shipment s JOIN trade_order o ON o.order_id = s.ord_no "
     "WHERE (s.sign_time IS NOT NULL) <> (o.order_status IN ('completed','refunded'))"),
    ("review: completed order, same user, after sign",
     "SELECT COUNT(*) FROM item_review r JOIN trade_order_item i ON i.id = r.order_item_id "
     "JOIN trade_order o ON o.order_id = i.order_id JOIN lgs_shipment s ON s.ord_no = o.order_id "
     "WHERE o.order_status <> 'completed' OR r.user_id <> o.buyer_id OR r.sku_id <> i.sku_id "
     "OR r.review_time <= s.sign_time OR r.rating NOT BETWEEN 1 AND 5"),
    ("aftersale after sign",
     "SELECT COUNT(*) FROM aftersale_order a JOIN trade_order_item i ON i.id = a.order_item_id "
     "JOIN lgs_shipment s ON s.ord_no = i.order_id WHERE s.sign_time IS NULL OR a.apply_time <= s.sign_time"),
    ("used coupon: same user, receive <= use = order_time",
     "SELECT COUNT(*) FROM mkt_coupon_receive c JOIN trade_order o ON o.order_id = c.used_order_id "
     "WHERE c.user_id <> o.buyer_id OR c.use_time <> o.order_time OR c.receive_time > c.use_time"),
    ("discount <=> used coupon",
     "SELECT COUNT(*) FROM trade_order o LEFT JOIN mkt_coupon_receive c ON c.used_order_id = o.order_id "
     "WHERE (o.discount_amt > 0) <> (c.used_order_id IS NOT NULL)"),
    ("visit after register",
     "SELECT COUNT(*) FROM log_visit v JOIN user_info u ON u.user_id = v.visitor_id WHERE v.visit_time < u.register_time"),
    ("exactly one default address per user",
     "SELECT COUNT(*) FROM (SELECT user_id FROM user_address GROUP BY user_id HAVING SUM(is_default) <> 1) t"),
    ("every sku brand in item_brand",
     "SELECT COUNT(*) FROM item_sku s LEFT JOIN item_brand b ON b.brand_name = s.brand_name WHERE b.brand_id IS NULL"),
]


def _layer_manifest_checks(cfg):
    """layer_rules 生成的对账清单：每个 DWS / ADS 可加和指标的总额必须等于其来源 DWD 的总额。"""
    path = Path(cfg["out_dir"]) / "layer_manifest.json"
    if not path.exists():
        return []
    return [(c["name"], c["sql"]) for c in json.loads(path.read_text(encoding="utf-8"))["checks"]]


def _norm_value(v):
    if isinstance(v, (int, float, decimal.Decimal)) and not isinstance(v, bool):
        return f"{decimal.Decimal(str(v)).quantize(decimal.Decimal('0.01'))}"
    if isinstance(v, (dt.date, dt.datetime)):
        return v.isoformat()
    return v


def _norm_result(rows, order_matters):
    out = [tuple(_norm_value(v) for v in r) for r in rows]
    return out if order_matters else sorted(out, key=repr)


def check_questions(cfg, conn):
    results = []
    for qfile in cfg["questions"]:
        for line in Path(qfile).read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            q = json.loads(line)
            r = {"id": q["id"], "ok": True}
            try:
                gold = db.execute(conn, q["gold_sql"])
                r["rows"] = len(gold)
                non_null = any(v is not None for row in gold for v in row)
                if not gold or not non_null:
                    r.update(ok=False, error="gold result empty")
                gold_n = _norm_result(gold, q.get("order_matters", False))
                for alt in q.get("alt_sqls", []):
                    if _norm_result(db.execute(conn, alt), q.get("order_matters", False)) != gold_n:
                        r.update(ok=False, error=f"alt mismatch: {alt[:60]}...")
                r["preview"] = [list(map(str, row)) for row in gold[:3]]
            except Exception as e:  # noqa: BLE001 - 报告中记录任意 SQL 错误
                r.update(ok=False, error=str(e))
            results.append(r)
    return results


def stats(cfg, conn):
    noise = db.noise_tables(cfg)
    database = cfg["database"]
    groups = {}
    for t in db.list_tables(conn, database):
        groups.setdefault(db.table_group(t, cfg["core_tables"], noise), []).append(t)
    col_rows = db.execute(conn, "SELECT TABLE_NAME, COUNT(*), SUM(COLUMN_COMMENT <> '') FROM information_schema.COLUMNS "
                                "WHERE TABLE_SCHEMA=%s GROUP BY TABLE_NAME", (database,))
    cols = {t: (int(n), int(c)) for t, n, c in col_rows}
    tbl_comment = dict(db.execute(conn, "SELECT TABLE_NAME, TABLE_COMMENT <> '' FROM information_schema.TABLES "
                                        "WHERE TABLE_SCHEMA=%s", (database,)))
    fk = db.scalar(conn, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                         "WHERE TABLE_SCHEMA=%s AND CONSTRAINT_TYPE='FOREIGN KEY'", (database,))
    by_group = {}
    for g, ts in sorted(groups.items()):
        n_cols = sum(cols[t][0] for t in ts)
        by_group[g] = {
            "tables": len(ts),
            "columns": n_cols,
            "table_comment_coverage": round(sum(int(tbl_comment[t]) for t in ts) / len(ts), 3),
            "column_comment_coverage": round(sum(cols[t][1] for t in ts) / n_cols, 3) if n_cols else 0,
        }
    core_rows = {t: db.scalar(conn, f"SELECT COUNT(*) FROM `{t}`") for t in cfg["core_tables"]}
    return {"total_tables": sum(len(v) for v in groups.values()), "total_columns": sum(v[0] for v in cols.values()),
            "foreign_keys": fk, "by_group": by_group, "core_rows": core_rows}


def run(cfg, conn):
    report = {
        "consistency": {name: db.scalar(conn, sql) for name, sql in CONSISTENCY + (CONSISTENCY_S2 if cfg.get("core_ext") else [])},
        "layers": {},
        "anomalies": {name: db.scalar(conn, sql) for name, sql in ANOMALIES},
        "stats": stats(cfg, conn),
        "questions": check_questions(cfg, conn),
    }
    for name, sql in LAYER_CHECKS + _layer_manifest_checks(cfg):
        a, b = db.execute(conn, sql)[0]
        report["layers"][name] = {"derived": str(a), "core": str(b), "ok": a == b}

    failures = [k for k, v in report["consistency"].items() if v]
    failures += [k for k, v in report["layers"].items() if not v["ok"]]
    failures += [q["id"] for q in report["questions"] if not q["ok"]]
    report["failures"] = failures

    out = Path(cfg["out_dir"]) / "validate_report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=1, default=str), encoding="utf-8")

    s = report["stats"]
    print(f"  tables={s['total_tables']} columns={s['total_columns']} fks={s['foreign_keys']}")
    for g, v in s["by_group"].items():
        print(f"    {g:8s} {v}")
    print(f"  core rows: {s['core_rows']}")
    print(f"  consistency violations: {sum(report['consistency'].values())}")
    print(f"  layer checks: {sum(v['ok'] for v in report['layers'].values())}/{len(report['layers'])} ok")
    print(f"  anomalies (injected): {report['anomalies']}")
    print(f"  questions: {sum(q['ok'] for q in report['questions'])}/{len(report['questions'])} ok")
    for q in report["questions"]:
        if not q["ok"]:
            print(f"    FAIL {q['id']}: {q.get('error')}")
    print(f"  report -> {out}")
    return 1 if failures else 0
