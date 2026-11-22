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

"""核心业务库数据生成：先写 CSV，再 LOAD DATA 导入。

一致性约束在生成时强制（validate.py 负责事后校验）：
- item_amount = qty × unit_price；unit_price = 商品当前售价
- total_amount = Σ item_amount；pay_amount = total_amount - discount_amt；0 ≤ discount_amt < total_amount
- 已支付类状态（paid/shipped/completed/refunded）有 pay_time 且晚于 order_time；created/cancelled 无 pay_time
- order_time ≥ 买家 register_time
"""
import csv
import datetime as dt
import random
from pathlib import Path

from . import db, dist

NULL = r"\N"
PAID_STATUSES = ("paid", "shipped", "completed", "refunded")
STATUS_WEIGHTS = {"created": 3, "cancelled": 7, "paid": 5, "shipped": 8, "completed": 72, "refunded": 5}
# 用户活跃分层：(名称, 占比, 下单权重, 会员等级候选)
USER_TIERS = [("high", 0.10, 8.0, (4, 5)), ("normal", 0.40, 2.0, (2, 3)),
              ("low", 0.30, 0.6, (1, 2)), ("dormant", 0.20, 0.1, (1,))]


def _yuan(cents):
    return f"{cents // 100}.{cents % 100:02d}"


def _ts(t):
    return t.strftime("%Y-%m-%d %H:%M:%S")


class _CsvWriter:
    def __init__(self, path, columns):
        self.columns = columns
        self.f = open(path, "w", newline="", encoding="utf-8")
        self.w = csv.writer(self.f, lineterminator="\n")
        self.w.writerow(columns)
        self.rows = 0

    def write(self, row):
        self.w.writerow(NULL if v is None else v for v in row)
        self.rows += 1

    def close(self):
        self.f.close()


def generate(cfg):
    rng = random.Random(cfg["seed"])
    scale = cfg["scale"]
    year = scale["year"]
    csv_dir = Path(cfg["out_dir"]) / "csv"
    csv_dir.mkdir(parents=True, exist_ok=True)
    files = {}

    # ---- 品类 ----
    cols = ["category_id", "category_name", "parent_id", "cate_level"]
    w = _CsvWriter(csv_dir / "item_category.csv", cols)
    leaf_categories = []  # (category_id, (low, high), nouns)
    cid = 100
    for top, children in dist.CATEGORIES.items():
        cid += 1
        top_id = cid * 100
        w.write([top_id, top, None, 1])
        for i, (name, price_range, nouns) in enumerate(children, start=1):
            w.write([top_id + i, name, top_id, 2])
            leaf_categories.append((top_id + i, price_range, nouns))
    w.close()
    files["item_category"] = (w, cols)

    # ---- 商品 ----
    cols = ["sku_id", "sku_name", "category_id", "brand_name", "price", "stock", "sku_status", "created_time"]
    w = _CsvWriter(csv_dir / "item_sku.csv", cols)
    skus = []  # (sku_id, price_cents)
    negative = set(rng.sample(range(scale["skus"]), cfg["anomalies"]["negative_stock_skus"]))
    sku_created_end = dt.datetime(year, 1, 1)
    for i in range(scale["skus"]):
        cat_id, (lo, hi), nouns = rng.choice(leaf_categories)
        sku_id = 10_000_001 + i
        price = dist.money_cents(rng, lo, hi)
        brand = rng.choice(dist.BRANDS)
        name = f"{brand} {rng.choice(nouns)} {rng.choice(['标准版', '升级版', 'Pro', '经典款', '旗舰款'])}"
        stock = -rng.randint(1, 20) if i in negative else rng.randint(0, 5000)
        status = "off_shelf" if rng.random() < 0.08 else "on_sale"
        created = sku_created_end - dt.timedelta(days=rng.randint(1, 720), seconds=rng.randint(0, 86399))
        w.write([sku_id, name, cat_id, brand, _yuan(price), stock, status, _ts(created)])
        skus.append((sku_id, price))
    w.close()
    files["item_sku"] = (w, cols)

    # ---- 用户 ----
    cols = ["user_id", "user_name", "gender", "birth_year", "province", "city", "member_level", "register_time"]
    w = _CsvWriter(csv_dir / "user_info.csv", cols)
    pick_province = dist.weighted_sampler(rng, dist.PROVINCES, [p[1] for p in dist.PROVINCES])
    pick_tier = dist.weighted_sampler(rng, USER_TIERS, [t[1] for t in USER_TIERS])
    reg_start, reg_end = dt.datetime(year - 2, 1, 1), dt.datetime(year, 12, 1)
    reg_span = int((reg_end - reg_start).total_seconds())
    users = []  # (user_id, register_time, province, order_weight)
    for i in range(scale["users"]):
        uid = 1_000_001 + i
        province, _, cities = pick_province()
        city = None if rng.random() < cfg["anomalies"]["user_city_null_ratio"] else rng.choice(cities)
        _, _, weight, levels = pick_tier()
        reg = reg_start + dt.timedelta(seconds=rng.randrange(reg_span))
        gender = rng.choices("MFU", weights=(48, 48, 4))[0]
        birth = rng.randint(1960, 2006) if rng.random() > 0.05 else None
        w.write([uid, dist.person_name(rng), gender, birth, province, city, rng.choice(levels), _ts(reg)])
        users.append((uid, reg, province, weight))
    w.close()
    files["user_info"] = (w, cols)

    # ---- 订单与明细 ----
    days, day_w = dist.day_weights(year)
    pick_day = dist.weighted_sampler(rng, days, day_w)
    pick_hour = dist.weighted_sampler(rng, list(range(24)), dist.HOUR_WEIGHTS)
    order_times = sorted(
        dt.datetime.combine(pick_day(), dt.time(pick_hour(), rng.randrange(60), rng.randrange(60)))
        for _ in range(scale["orders"])
    )
    pick_user = dist.weighted_sampler(rng, users, [u[3] for u in users])
    sku_rank = skus[:]
    rng.shuffle(sku_rank)  # 打乱后按 Zipf 赋热度，避免 ID 小的商品天然热门
    pick_sku = dist.weighted_sampler(rng, sku_rank, dist.zipf_weights(len(sku_rank)))
    pick_status = dist.weighted_sampler(rng, list(STATUS_WEIGHTS), list(STATUS_WEIGHTS.values()))
    all_provinces = [p[0] for p in dist.PROVINCES]

    order_cols = ["order_id", "buyer_id", "order_status", "total_amount", "discount_amt", "pay_amount",
                  "receiver_province", "order_time", "pay_time"]
    item_cols = ["id", "order_id", "sku_id", "qty", "unit_price", "item_amount"]
    wo = _CsvWriter(csv_dir / "trade_order.csv", order_cols)
    wi = _CsvWriter(csv_dir / "trade_order_item.csv", item_cols)
    item_id = 50_000_001
    for n, order_time in enumerate(order_times):
        order_id = 2_000_000_001 + n
        for _ in range(30):  # 拒绝采样：买家必须在下单前已注册
            uid, reg, province, _ = pick_user()
            if reg <= order_time:
                break
        else:
            uid, reg, province, _ = min(users, key=lambda u: u[1])

        n_items = rng.choices((1, 2, 3, 4, 5), weights=(55, 25, 12, 5, 3))[0]
        chosen = {}
        while len(chosen) < n_items:
            sku_id, price = pick_sku()
            chosen[sku_id] = price
        total = 0
        for sku_id, price in chosen.items():
            qty = rng.choices((1, 2, 3), weights=(80, 15, 5))[0]
            amount = qty * price
            total += amount
            wi.write([item_id, order_id, sku_id, qty, _yuan(price), _yuan(amount)])
            item_id += 1

        discount = int(total * rng.uniform(0.05, 0.2)) if rng.random() < 0.3 else 0
        status = pick_status()
        pay_time = order_time + dt.timedelta(seconds=rng.randint(10, 1800)) if status in PAID_STATUSES else None
        receiver = province if rng.random() < 0.9 else rng.choice(all_provinces)
        wo.write([order_id, uid, status, _yuan(total), _yuan(discount), _yuan(total - discount),
                  receiver, _ts(order_time), _ts(pay_time) if pay_time else None])
    wo.close()
    wi.close()
    files["trade_order"] = (wo, order_cols)
    files["trade_order_item"] = (wi, item_cols)
    return {t: (str(Path(cfg["out_dir"]) / "csv" / f"{t}.csv"), cols, w.rows) for t, (w, cols) in files.items()}


def run(cfg, conn):
    db.run_sql_file(conn, "ddl/core/core_m0.sql")
    produced = generate(cfg)
    # 按外键依赖顺序导入
    for table in ["item_category", "item_sku", "user_info", "trade_order", "trade_order_item"]:
        path, cols, rows = produced[table]
        db.load_csv(conn, table, path, cols)
        print(f"  {table}: {rows} rows")
