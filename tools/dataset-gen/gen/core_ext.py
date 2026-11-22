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

"""核心业务库 S2 扩展：在 core 步骤导入的 5 张表之上，派生 11 张新核心表（ddl/core/core_s2.sql）。

全部用 INSERT ... SELECT 在库内从已有核心表计算，随机性来自确定性哈希：
    R(表达式, 盐) = CRC32(CONCAT(种子, ':', 盐, ':', 表达式))
同一种子结果完全一致；业务约束（金额、时间先后、状态对应）在构造时即成立，validate 再逐条校验。
"""
from . import db

# 订单收货省份 → 仓库（大区）
REGION_OF_PROVINCE = {
    "上海市": 1, "江苏省": 1, "浙江省": 1, "安徽省": 1, "山东省": 1,
    "广东省": 2, "福建省": 2,
    "北京市": 3, "天津市": 3, "河北省": 3,
    "湖北省": 4, "湖南省": 4, "河南省": 4,
    "四川省": 5, "重庆市": 5,
    "陕西省": 6,
}
WAREHOUSES = [(1, "上海青浦仓", "华东", "上海市"), (2, "广州白云仓", "华南", "广东省"),
              (3, "北京大兴仓", "华北", "北京市"), (4, "武汉东西湖仓", "华中", "湖北省"),
              (5, "成都双流仓", "西南", "四川省"), (6, "西安临潼仓", "西北", "陕西省")]


def _r(seed, expr, salt):
    return f"CRC32(CONCAT('{seed}', ':{salt}:', {expr}))"


def _warehouse_case(col):
    whens = " ".join(f"WHEN '{p}' THEN {w}" for p, w in REGION_OF_PROVINCE.items())
    return f"CASE {col} {whens} ELSE 1 END"


def run(cfg, conn):
    ext = cfg.get("core_ext")
    if not ext:
        print("  core_ext disabled")
        return
    seed = cfg["seed"]
    R = lambda expr, salt: _r(seed, expr, salt)  # noqa: E731
    db.run_sql_file(conn, "ddl/core/core_s2.sql")

    # 辅助序列表 1..N，用完删除
    seq_max = max(ext["extra_visits"], 100)
    db.execute(conn, "SET SESSION cte_max_recursion_depth = %s", (seq_max + 10,))
    db.execute(conn, "DROP TABLE IF EXISTS _seq")
    db.execute(conn, "CREATE TABLE _seq (n INT PRIMARY KEY)")
    db.execute(conn, f"INSERT INTO _seq WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s "
                     f"WHERE n < {seq_max}) SELECT n FROM s")
    users = db.scalar(conn, "SELECT COUNT(*) FROM user_info")
    skus = db.scalar(conn, "SELECT COUNT(*) FROM item_sku")
    other_provinces = "'上海市','北京市','广东省','浙江省','江苏省'"

    statements = {
        "user_address": f"""
INSERT INTO user_address
SELECT u.user_id * 10 + s.n, u.user_id, u.user_name,
       IF(s.n = 1 OR MOD({R('u.user_id', 'addr_p')}, 4) > 0, u.province,
          ELT(1 + MOD({R('u.user_id', 'addr_q')}, 5), {other_provinces})),
       IF(s.n = 1, u.city, NULL), ELT(s.n, 'home', 'company', 'other'), IF(s.n = 1, 1, 0),
       u.register_time + INTERVAL s.n DAY
FROM user_info u JOIN _seq s ON s.n <= 1 + MOD({R('u.user_id', 'addr_n')}, 3)""",
        "item_brand": f"""
INSERT INTO item_brand
SELECT ROW_NUMBER() OVER (ORDER BY b.brand_name), b.brand_name,
       ELT(1 + MOD({R('b.brand_name', 'bc')}, 4), '中国', '日本', '德国', '美国'),
       1950 + MOD({R('b.brand_name', 'by')}, 70), ELT(1 + MOD({R('b.brand_name', 'bl')}, 3), 'S', 'A', 'B')
FROM (SELECT DISTINCT brand_name FROM item_sku) b""",
        "mkt_coupon": """
INSERT INTO mkt_coupon
SELECT n, CONCAT(ELT(1 + MOD(n, 3), '满减券', '折扣券', '无门槛券'), '-', LPAD(n, 2, '0')),
       ELT(1 + MOD(n, 3), 'full_reduction', 'discount', 'no_threshold'),
       IF(MOD(n, 3) = 2, 0, 100 * (1 + MOD(n, 5))), 5 * (1 + MOD(n, 10)), '2025-01-01', '2025-12-31'
FROM _seq WHERE n <= 30""",
        "mkt_coupon_receive_used": f"""
INSERT INTO mkt_coupon_receive
SELECT o.order_id, 1 + MOD({R('o.order_id', 'cp')}, 30), o.buyer_id,
       GREATEST(o.order_time - INTERVAL (1 + MOD({R('o.order_id', 'cph')}, 72)) HOUR, u.register_time),
       'used', o.order_id, o.order_time
FROM trade_order o JOIN user_info u ON u.user_id = o.buyer_id
WHERE o.discount_amt > 0""",
        "mkt_coupon_receive_unused": f"""
INSERT INTO mkt_coupon_receive
SELECT u.user_id, 1 + MOD({R('u.user_id', 'ucp')}, 30), u.user_id,
       GREATEST('2025-01-01' + INTERVAL MOD({R('u.user_id', 'ucd')}, 360) DAY, u.register_time),
       'unused', NULL, NULL
FROM user_info u WHERE MOD({R('u.user_id', 'unused')}, 3) = 0""",
        "pay_order": f"""
INSERT INTO pay_order
SELECT o.order_id * 10 + 1, o.order_id, o.buyer_id,
       ELT(1 + MOD({R('o.order_id', 'ch')}, 10), 'alipay', 'alipay', 'alipay', 'alipay', 'wechat', 'wechat',
           'wechat', 'card', 'card', 'unionpay'),
       o.pay_amount, 'success', o.pay_time
FROM trade_order o WHERE o.pay_time IS NOT NULL""",
        "pay_refund": f"""
INSERT INTO pay_refund
SELECT o.order_id * 10 + 2, o.order_id,
       ROUND(o.pay_amount * (30 + MOD({R('o.order_id', 'ra')}, 71)) / 100, 2),
       ELT(1 + MOD({R('o.order_id', 'rr')}, 4), 'quality', 'wrong_item', 'no_longer_needed', 'late_delivery'),
       t.apply_time, t.apply_time + INTERVAL (1 + MOD({R('o.order_id', 'rf')}, 3)) DAY
FROM trade_order o
JOIN (SELECT order_id, pay_time + INTERVAL (1 + MOD({R('order_id', 'rd')}, 10)) DAY
             + INTERVAL MOD({R('order_id', 'rs')}, 86400) SECOND AS apply_time
      FROM trade_order WHERE order_status = 'refunded') t ON t.order_id = o.order_id""",
        "lgs_warehouse": "INSERT INTO lgs_warehouse VALUES " + ", ".join(
            f"({w}, '{n}', '{r}', '{p}')" for w, n, r, p in WAREHOUSES),
        "lgs_shipment": f"""
INSERT INTO lgs_shipment
SELECT o.order_id * 10 + 3, o.order_id, {_warehouse_case('o.receiver_province')},
       ELT(1 + MOD({R('o.order_id', 'cr')}, 4), 'sf', 'jd', 'yto', 'zto'), t.ship_time,
       IF(o.order_status IN ('completed', 'refunded'),
          t.ship_time + INTERVAL (1 + MOD({R('o.order_id', 'sd')}, 5)) DAY
          + INTERVAL MOD({R('o.order_id', 'sh')}, 20) HOUR, NULL)
FROM trade_order o
JOIN (SELECT order_id, pay_time + INTERVAL (2 + MOD({R('order_id', 'st')}, 46)) HOUR AS ship_time
      FROM trade_order WHERE order_status IN ('shipped', 'completed', 'refunded')) t ON t.order_id = o.order_id""",
        "aftersale_order": f"""
INSERT INTO aftersale_order
SELECT i.id * 10 + 4, i.id, ELT(1 + MOD({R('i.id', 'at')}, 3), 'return', 'exchange', 'repair'),
       sh.sign_time + INTERVAL (1 + MOD({R('i.id', 'ad')}, 7)) DAY,
       ELT(1 + MOD({R('i.id', 'ast')}, 5), 'closed', 'closed', 'closed', 'processing', 'rejected')
FROM trade_order_item i JOIN trade_order o ON o.order_id = i.order_id
JOIN lgs_shipment sh ON sh.ord_no = o.order_id
WHERE o.order_status = 'completed' AND MOD({R('i.id', 'as')}, 25) = 0""",
        "item_review": f"""
INSERT INTO item_review
SELECT i.id * 10 + 5, i.id, i.sku_id, o.buyer_id,
       ELT(1 + MOD({R('i.id', 'rt')}, 20), 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 4, 4, 4, 4, 4, 4, 3, 3, 2, 1),
       IF(MOD({R('i.id', 'img')}, 4) = 0, 1, 0),
       sh.sign_time + INTERVAL (1 + MOD({R('i.id', 'rvd')}, 10)) DAY + INTERVAL MOD({R('i.id', 'rvh')}, 24) HOUR
FROM trade_order_item i JOIN trade_order o ON o.order_id = i.order_id
JOIN lgs_shipment sh ON sh.ord_no = o.order_id
WHERE o.order_status = 'completed' AND MOD({R('i.id', 'rv')}, 3) < 2""",
        "log_visit_before_order": f"""
INSERT INTO log_visit
SELECT i.id * 10 + 6, o.buyer_id, i.sku_id, ELT(1 + MOD({R('i.id', 'vc')}, 3), 'app', 'mini_program', 'web'),
       GREATEST(o.order_time - INTERVAL (1 + MOD({R('i.id', 'vm')}, 240)) MINUTE, u.register_time),
       10 + MOD({R('i.id', 'vs')}, 600)
FROM trade_order_item i JOIN trade_order o ON o.order_id = i.order_id
JOIN user_info u ON u.user_id = o.buyer_id""",
        "log_visit_browse": f"""
INSERT INTO log_visit
SELECT 9000000000 + s.n, u.user_id, 10000001 + MOD({R('s.n', 'vsk')}, {skus}),
       ELT(1 + MOD({R('s.n', 'vch')}, 3), 'app', 'mini_program', 'web'),
       GREATEST('2025-01-01' + INTERVAL MOD({R('s.n', 'vt')}, 31536000) SECOND, u.register_time),
       5 + MOD({R('s.n', 'vst')}, 300)
FROM _seq s JOIN user_info u ON u.user_id = 1000001 + MOD({R('s.n', 'vu')}, {users})
WHERE s.n <= {int(ext['extra_visits'])}""",
    }
    for name, sql in statements.items():
        db.execute(conn, "SET SESSION foreign_key_checks = 0")
        db.execute(conn, sql)
        db.execute(conn, "SET SESSION foreign_key_checks = 1")
    db.execute(conn, "DROP TABLE _seq")
    for t in ["user_address", "item_brand", "mkt_coupon", "mkt_coupon_receive", "pay_order", "pay_refund",
              "lgs_warehouse", "lgs_shipment", "aftersale_order", "item_review", "log_visit"]:
        print(f"  {t}: {db.scalar(conn, f'SELECT COUNT(*) FROM `{t}`')} rows")
