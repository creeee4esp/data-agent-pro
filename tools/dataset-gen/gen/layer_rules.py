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

"""S2 分层派生规则：在 ODS 和 M0 手写分层（trade_m0.sql）之上，按规则批量生成 DIM / DWD / DWS / ADS。

原则（ADR-003）：下游一律在库内 INSERT ... SELECT 从上游计算。
DWS 由"事件 × 粒度 × 周期"模板生成；可加和指标（SUM / COUNT(*)）的总额写入 out/layer_manifest.json，
由 validate 逐表对账：DWS 总额必须等于来源 DWD 的总额。
"""
import json
from pathlib import Path

from . import db

# ---------- DIM / DWD：固定定义 ----------

REGION_CASE = "CASE {col} " + " ".join(
    f"WHEN '{p}' THEN '{r}'" for p, r in {
        "上海市": "华东", "江苏省": "华东", "浙江省": "华东", "安徽省": "华东", "山东省": "华东",
        "广东省": "华南", "福建省": "华南", "北京市": "华北", "天津市": "华北", "河北省": "华北",
        "湖北省": "华中", "湖南省": "华中", "河南省": "华中", "四川省": "西南", "重庆市": "西南",
        "陕西省": "西北"}.items()) + " ELSE '其他' END"

FIXED_TABLES = [
    ("dim_region", "DIM-省份与大区维表", """
CREATE TABLE dim_region (province VARCHAR(20) NOT NULL COMMENT '省份', region VARCHAR(10) NOT NULL COMMENT '大区',
  PRIMARY KEY (province))""", f"""
INSERT INTO dim_region SELECT p, {REGION_CASE.format(col='p')} FROM (SELECT DISTINCT province AS p FROM ods_user_info_di
  UNION SELECT DISTINCT receiver_province FROM ods_trade_order_di) t"""),
    ("dim_sku", "DIM-商品维表（含品牌、两级品类）", """
CREATE TABLE dim_sku (sku_id BIGINT NOT NULL COMMENT '商品SKU ID', sku_name VARCHAR(100) COMMENT '商品名称',
  price DECIMAL(10,2) COMMENT '当前售价（元）', sku_status VARCHAR(20) COMMENT '商品状态',
  brand_id INT COMMENT '品牌ID', brand_name VARCHAR(50) COMMENT '品牌名称', brand_level VARCHAR(10) COMMENT '品牌等级',
  category2_id INT COMMENT '二级品类ID', category2_name VARCHAR(50) COMMENT '二级品类名称',
  category1_id INT COMMENT '一级品类ID', category1_name VARCHAR(50) COMMENT '一级品类名称', PRIMARY KEY (sku_id))""", """
INSERT INTO dim_sku SELECT s.sku_id, s.sku_name, s.price, s.sku_status, b.brand_id, s.brand_name, b.brand_level,
  c2.category_id, c2.category_name, c1.category_id, c1.category_name
FROM ods_item_sku_di s LEFT JOIN ods_item_brand_di b ON b.brand_name = s.brand_name
LEFT JOIN ods_item_category_di c2 ON c2.category_id = s.category_id
LEFT JOIN ods_item_category_di c1 ON c1.category_id = c2.parent_id"""),
    ("dim_user", "DIM-用户维表", """
CREATE TABLE dim_user (user_id BIGINT NOT NULL COMMENT '用户ID', user_name VARCHAR(50) COMMENT '用户姓名',
  gender CHAR(1) COMMENT '性别', age_group VARCHAR(10) COMMENT '年龄段', province VARCHAR(20) COMMENT '常住省份',
  region VARCHAR(10) COMMENT '大区', member_level TINYINT COMMENT '会员等级', register_date DATE COMMENT '注册日期',
  address_cnt INT COMMENT '收货地址数', PRIMARY KEY (user_id))""", f"""
INSERT INTO dim_user SELECT u.user_id, u.user_name, u.gender,
  CASE WHEN u.birth_year IS NULL THEN '未知' WHEN u.birth_year >= 2000 THEN '00后' WHEN u.birth_year >= 1990 THEN '90后'
       WHEN u.birth_year >= 1980 THEN '80后' ELSE '80前' END,
  u.province, {REGION_CASE.format(col='u.province')}, u.member_level, DATE(u.register_time),
  (SELECT COUNT(*) FROM ods_user_address_di a WHERE a.user_id = u.user_id)
FROM ods_user_info_di u"""),
    ("dim_user_zip", "DIM-用户会员等级拉链表", """
CREATE TABLE dim_user_zip (user_id BIGINT NOT NULL COMMENT '用户ID', member_level TINYINT COMMENT '会员等级',
  start_date DATE NOT NULL COMMENT '生效开始日期', end_date DATE NOT NULL COMMENT '生效结束日期，9999-12-31 表示当前有效',
  PRIMARY KEY (user_id, start_date))""", """
INSERT INTO dim_user_zip SELECT user_id, member_level, DATE(register_time), '9999-12-31' FROM ods_user_info_di"""),
    ("dim_coupon", "DIM-优惠券维表", """
CREATE TABLE dim_coupon (coupon_id INT NOT NULL COMMENT '优惠券ID', coupon_name VARCHAR(50) COMMENT '优惠券名称',
  coupon_type VARCHAR(20) COMMENT '类型', threshold_amt DECIMAL(10,2) COMMENT '门槛金额（元）',
  face_value DECIMAL(10,2) COMMENT '面额（元）', PRIMARY KEY (coupon_id))""", """
INSERT INTO dim_coupon SELECT coupon_id, coupon_name, coupon_type, threshold_amt, face_value FROM ods_mkt_coupon_di"""),
    ("dwd_trade_pay_di", "DWD-支付明细", """
CREATE TABLE dwd_trade_pay_di (pay_id BIGINT NOT NULL COMMENT '支付单ID', order_id BIGINT COMMENT '订单ID',
  buyer_id BIGINT COMMENT '买家用户ID', pay_channel VARCHAR(20) COMMENT '支付渠道', pay_amt DECIMAL(12,2) COMMENT '支付金额（元）',
  receiver_province VARCHAR(20) COMMENT '收货省份', pay_date DATE COMMENT '支付日期', dt DATE COMMENT '分区日期',
  PRIMARY KEY (pay_id))""", """
INSERT INTO dwd_trade_pay_di SELECT p.pay_id, p.order_id, p.uid, p.pay_channel, p.pay_amt, o.receiver_province,
  DATE(p.pay_time), p.dt FROM ods_pay_order_di p JOIN ods_trade_order_di o ON o.order_id = p.order_id"""),
    ("dwd_trade_refund_di", "DWD-退款明细", """
CREATE TABLE dwd_trade_refund_di (refund_id BIGINT NOT NULL COMMENT '退款单ID', order_id BIGINT COMMENT '订单ID',
  buyer_id BIGINT COMMENT '买家用户ID', refund_amount DECIMAL(12,2) COMMENT '退款金额（元）',
  refund_reason VARCHAR(20) COMMENT '退款原因', receiver_province VARCHAR(20) COMMENT '收货省份',
  refund_date DATE COMMENT '退款完成日期', dt DATE COMMENT '分区日期', PRIMARY KEY (refund_id))""", """
INSERT INTO dwd_trade_refund_di SELECT r.refund_id, r.order_id, o.buyer_id, r.refund_amount, r.refund_reason,
  o.receiver_province, DATE(r.finish_time), r.dt FROM ods_pay_refund_di r JOIN ods_trade_order_di o ON o.order_id = r.order_id"""),
    ("dwd_lgs_shipment_di", "DWD-物流明细", """
CREATE TABLE dwd_lgs_shipment_di (ship_id BIGINT NOT NULL COMMENT '物流单ID', order_id BIGINT COMMENT '订单ID',
  warehouse_id INT COMMENT '仓库ID', warehouse_region VARCHAR(10) COMMENT '仓库大区', carrier VARCHAR(20) COMMENT '承运商',
  receiver_province VARCHAR(20) COMMENT '收货省份', ship_hours INT COMMENT '支付到发货的小时数',
  deliver_hours INT COMMENT '发货到签收的小时数，未签收为空', is_signed TINYINT COMMENT '是否已签收',
  ship_date DATE COMMENT '发货日期', dt DATE COMMENT '分区日期', PRIMARY KEY (ship_id))""", """
INSERT INTO dwd_lgs_shipment_di SELECT s.ship_id, s.ord_no, s.warehouse_id, w.region, s.carrier, o.receiver_province,
  TIMESTAMPDIFF(HOUR, o.pay_time, s.ship_time), TIMESTAMPDIFF(HOUR, s.ship_time, s.sign_time),
  IF(s.sign_time IS NULL, 0, 1), DATE(s.ship_time), s.dt
FROM ods_lgs_shipment_di s JOIN ods_trade_order_di o ON o.order_id = s.ord_no
JOIN ods_lgs_warehouse_di w ON w.warehouse_id = s.warehouse_id"""),
    ("dwd_item_review_di", "DWD-评价明细", """
CREATE TABLE dwd_item_review_di (review_id BIGINT NOT NULL COMMENT '评价ID', sku_id BIGINT COMMENT '商品SKU ID',
  user_id BIGINT COMMENT '用户ID', rating TINYINT COMMENT '评分', is_good TINYINT COMMENT '是否好评（4星及以上）',
  brand_name VARCHAR(50) COMMENT '品牌', category1_id INT COMMENT '一级品类ID', category1_name VARCHAR(50) COMMENT '一级品类名称',
  review_date DATE COMMENT '评价日期', dt DATE COMMENT '分区日期', PRIMARY KEY (review_id))""", """
INSERT INTO dwd_item_review_di SELECT r.review_id, r.sku_id, r.user_id, r.rating, IF(r.rating >= 4, 1, 0),
  d.brand_name, d.category1_id, d.category1_name, DATE(r.review_time), r.dt
FROM ods_item_review_di r JOIN dim_sku d ON d.sku_id = r.sku_id"""),
    ("dwd_traffic_visit_di", "DWD-商品访问明细", """
CREATE TABLE dwd_traffic_visit_di (visit_id BIGINT NOT NULL COMMENT '访问记录ID', visitor_id BIGINT COMMENT '访问用户ID',
  sku_id BIGINT COMMENT '商品SKU ID', channel VARCHAR(20) COMMENT '访问渠道', stay_seconds INT COMMENT '停留秒数',
  brand_name VARCHAR(50) COMMENT '品牌', category1_id INT COMMENT '一级品类ID', category1_name VARCHAR(50) COMMENT '一级品类名称',
  visit_date DATE COMMENT '访问日期', dt DATE COMMENT '分区日期', PRIMARY KEY (visit_id))""", """
INSERT INTO dwd_traffic_visit_di SELECT v.visit_id, v.visitor_id, v.sku_id, v.channel, v.stay_seconds,
  d.brand_name, d.category1_id, d.category1_name, DATE(v.visit_time), v.dt
FROM ods_log_visit_di v JOIN dim_sku d ON d.sku_id = v.sku_id"""),
]

# ---------- DWS：事件 × 粒度 × 周期 ----------

# 事件：(域, 事件名, 来源 DWD, 日期列, 中文名, 指标列表[(列名, 表达式, 类型, 注释, 是否可加和)])
EVENTS = {
    "trade": ("trade", "order", "dwd_trade_order_detail_di", "order_date", "下单", [
        ("order_cnt", "COUNT(DISTINCT order_id)", "INT", "下单数", False),
        ("item_qty", "SUM(qty)", "INT", "购买件数", True),
        ("paid_amount", "SUM(IF(is_paid = 1, item_amount, 0))", "DECIMAL(16,2)", "已支付订单的明细金额（元）", True)]),
    "pay": ("trade", "pay", "dwd_trade_pay_di", "pay_date", "支付", [
        ("pay_cnt", "COUNT(*)", "INT", "支付笔数", True),
        ("pay_amt", "SUM(pay_amt)", "DECIMAL(16,2)", "支付金额（元）", True)]),
    "refund": ("trade", "refund", "dwd_trade_refund_di", "refund_date", "退款", [
        ("refund_cnt", "COUNT(*)", "INT", "退款笔数", True),
        ("refund_amount", "SUM(refund_amount)", "DECIMAL(16,2)", "退款金额（元）", True)]),
    "ship": ("lgs", "ship", "dwd_lgs_shipment_di", "ship_date", "发货", [
        ("ship_cnt", "COUNT(*)", "INT", "发货单数", True),
        ("signed_cnt", "SUM(is_signed)", "INT", "已签收单数", True),
        ("avg_ship_hours", "ROUND(AVG(ship_hours), 2)", "DECIMAL(10,2)", "平均发货时长（小时）", False)]),
    "review": ("item", "review", "dwd_item_review_di", "review_date", "评价", [
        ("review_cnt", "COUNT(*)", "INT", "评价数", True),
        ("good_cnt", "SUM(is_good)", "INT", "好评数（4星及以上）", True),
        ("avg_rating", "ROUND(AVG(rating), 2)", "DECIMAL(4,2)", "平均评分", False)]),
    "visit": ("traffic", "visit", "dwd_traffic_visit_di", "visit_date", "访问", [
        ("pv", "COUNT(*)", "INT", "浏览量", True),
        ("uv", "COUNT(DISTINCT visitor_id)", "INT", "访客数", False),
        ("stay_seconds", "SUM(stay_seconds)", "BIGINT", "总停留秒数", True)]),
}

# 粒度：名字 → [(列名, 类型, 注释)]，列名必须存在于来源 DWD
GRAINS = {
    "user": [("buyer_id", "BIGINT", "买家用户ID")],
    "visitor": [("visitor_id", "BIGINT", "访问用户ID")],
    "sku": [("sku_id", "BIGINT", "商品SKU ID")],
    "cate1": [("category1_id", "INT", "一级品类ID"), ("category1_name", "VARCHAR(50)", "一级品类名称")],
    "cate2": [("category2_id", "INT", "二级品类ID"), ("category2_name", "VARCHAR(50)", "二级品类名称")],
    "brand": [("brand_name", "VARCHAR(50)", "品牌")],
    "prov": [("receiver_province", "VARCHAR(20)", "收货省份")],
    "level": [("member_level", "TINYINT", "会员等级")],
    "channel": [("pay_channel", "VARCHAR(20)", "支付渠道")],
    "reason": [("refund_reason", "VARCHAR(20)", "退款原因")],
    "warehouse": [("warehouse_id", "INT", "仓库ID"), ("warehouse_region", "VARCHAR(10)", "仓库大区")],
    "carrier": [("carrier", "VARCHAR(20)", "承运商")],
    "vchannel": [("channel", "VARCHAR(20)", "访问渠道")],
}

PERIODS = {
    "1d": ("{d}", "统计日期", "日"),
    "1w": ("DATE_SUB({d}, INTERVAL WEEKDAY({d}) DAY)", "统计周（周一日期）", "周"),
    "1m": ("DATE_FORMAT({d}, '%Y-%m-01')", "统计月（月初日期）", "月"),
}

# ---------- ADS：报表主题 ----------

ADS_TABLES = [
    ("ads_trade_gmv_monthly", "ADS-月度GMV报表", """
CREATE TABLE ads_trade_gmv_monthly (stat_month CHAR(7) NOT NULL COMMENT '统计月份，如 2025-06',
  gmv DECIMAL(16,2) COMMENT 'GMV：已支付订单明细金额之和（元）', paid_order_cnt INT COMMENT '支付订单数',
  paid_buyer_cnt INT COMMENT '支付买家数', PRIMARY KEY (stat_month))""", """
INSERT INTO ads_trade_gmv_monthly SELECT DATE_FORMAT(order_date, '%Y-%m'), SUM(IF(is_paid = 1, item_amount, 0)),
  COUNT(DISTINCT IF(is_paid = 1, order_id, NULL)), COUNT(DISTINCT IF(is_paid = 1, buyer_id, NULL))
FROM dwd_trade_order_detail_di GROUP BY DATE_FORMAT(order_date, '%Y-%m')""",
     [("gmv", "dwd_trade_order_detail_di", "SUM(IF(is_paid = 1, item_amount, 0))")]),
    ("ads_trade_cate1_sales_monthly", "ADS-一级品类月度销售额", """
CREATE TABLE ads_trade_cate1_sales_monthly (stat_month CHAR(7) NOT NULL COMMENT '统计月份',
  category1_name VARCHAR(50) NOT NULL COMMENT '一级品类名称', sales_amount DECIMAL(16,2) COMMENT '销售额：已支付明细金额（元）',
  item_qty INT COMMENT '销售件数（已支付）', PRIMARY KEY (stat_month, category1_name))""", """
INSERT INTO ads_trade_cate1_sales_monthly SELECT DATE_FORMAT(order_date, '%Y-%m'), category1_name,
  SUM(IF(is_paid = 1, item_amount, 0)), SUM(IF(is_paid = 1, qty, 0))
FROM dwd_trade_order_detail_di GROUP BY DATE_FORMAT(order_date, '%Y-%m'), category1_name""",
     [("sales_amount", "dwd_trade_order_detail_di", "SUM(IF(is_paid = 1, item_amount, 0))")]),
    ("ads_trade_province_sales_monthly", "ADS-省份月度销售额", """
CREATE TABLE ads_trade_province_sales_monthly (stat_month CHAR(7) NOT NULL COMMENT '统计月份',
  receiver_province VARCHAR(20) NOT NULL COMMENT '收货省份', sales_amount DECIMAL(16,2) COMMENT '销售额（元）',
  PRIMARY KEY (stat_month, receiver_province))""", """
INSERT INTO ads_trade_province_sales_monthly SELECT DATE_FORMAT(order_date, '%Y-%m'), receiver_province,
  SUM(IF(is_paid = 1, item_amount, 0)) FROM dwd_trade_order_detail_di
GROUP BY DATE_FORMAT(order_date, '%Y-%m'), receiver_province""",
     [("sales_amount", "dwd_trade_order_detail_di", "SUM(IF(is_paid = 1, item_amount, 0))")]),
    ("ads_pay_channel_monthly", "ADS-支付渠道月度报表", """
CREATE TABLE ads_pay_channel_monthly (stat_month CHAR(7) NOT NULL COMMENT '统计月份',
  pay_channel VARCHAR(20) NOT NULL COMMENT '支付渠道', pay_cnt INT COMMENT '支付笔数', pay_amt DECIMAL(16,2) COMMENT '支付金额（元）',
  PRIMARY KEY (stat_month, pay_channel))""", """
INSERT INTO ads_pay_channel_monthly SELECT DATE_FORMAT(pay_date, '%Y-%m'), pay_channel, COUNT(*), SUM(pay_amt)
FROM dwd_trade_pay_di GROUP BY DATE_FORMAT(pay_date, '%Y-%m'), pay_channel""",
     [("pay_amt", "dwd_trade_pay_di", "SUM(pay_amt)")]),
    ("ads_trade_refund_rate_monthly", "ADS-月度退款率", """
CREATE TABLE ads_trade_refund_rate_monthly (stat_month CHAR(7) NOT NULL COMMENT '统计月份（按下单月）',
  paid_order_cnt INT COMMENT '支付订单数', refund_order_cnt INT COMMENT '退款订单数',
  refund_rate DECIMAL(8,4) COMMENT '退款率 = 退款订单数 / 支付订单数', PRIMARY KEY (stat_month))""", """
INSERT INTO ads_trade_refund_rate_monthly SELECT DATE_FORMAT(o.order_time, '%Y-%m'), COUNT(*),
  SUM(IF(o.order_status = 'refunded', 1, 0)), ROUND(SUM(IF(o.order_status = 'refunded', 1, 0)) / COUNT(*), 4)
FROM ods_trade_order_di o WHERE o.pay_time IS NOT NULL GROUP BY DATE_FORMAT(o.order_time, '%Y-%m')""", []),
    ("ads_lgs_delivery_monthly", "ADS-仓库月度物流时效", """
CREATE TABLE ads_lgs_delivery_monthly (stat_month CHAR(7) NOT NULL COMMENT '统计月份（按发货月）',
  warehouse_region VARCHAR(10) NOT NULL COMMENT '仓库大区', ship_cnt INT COMMENT '发货单数',
  avg_ship_hours DECIMAL(10,2) COMMENT '平均发货时长（小时）', avg_deliver_hours DECIMAL(10,2) COMMENT '平均配送时长（小时，已签收）',
  PRIMARY KEY (stat_month, warehouse_region))""", """
INSERT INTO ads_lgs_delivery_monthly SELECT DATE_FORMAT(ship_date, '%Y-%m'), warehouse_region, COUNT(*),
  ROUND(AVG(ship_hours), 2), ROUND(AVG(deliver_hours), 2) FROM dwd_lgs_shipment_di
GROUP BY DATE_FORMAT(ship_date, '%Y-%m'), warehouse_region""",
     [("ship_cnt", "dwd_lgs_shipment_di", "COUNT(*)")]),
    ("ads_item_review_score_monthly", "ADS-一级品类月度评分", """
CREATE TABLE ads_item_review_score_monthly (stat_month CHAR(7) NOT NULL COMMENT '统计月份',
  category1_name VARCHAR(50) NOT NULL COMMENT '一级品类名称', review_cnt INT COMMENT '评价数',
  avg_rating DECIMAL(4,2) COMMENT '平均评分', good_rate DECIMAL(8,4) COMMENT '好评率', PRIMARY KEY (stat_month, category1_name))""", """
INSERT INTO ads_item_review_score_monthly SELECT DATE_FORMAT(review_date, '%Y-%m'), category1_name, COUNT(*),
  ROUND(AVG(rating), 2), ROUND(SUM(is_good) / COUNT(*), 4) FROM dwd_item_review_di
GROUP BY DATE_FORMAT(review_date, '%Y-%m'), category1_name""",
     [("review_cnt", "dwd_item_review_di", "COUNT(*)")]),
    ("ads_traffic_conversion_daily", "ADS-每日访问转化", """
CREATE TABLE ads_traffic_conversion_daily (stat_date DATE NOT NULL COMMENT '统计日期', uv INT COMMENT '访客数',
  buyer_cnt INT COMMENT '下单用户数', conversion_rate DECIMAL(8,4) COMMENT '转化率 = 下单用户数 / 访客数',
  PRIMARY KEY (stat_date))""", """
INSERT INTO ads_traffic_conversion_daily SELECT v.visit_date, v.uv, COALESCE(o.buyers, 0), ROUND(COALESCE(o.buyers, 0) / v.uv, 4)
FROM (SELECT visit_date, COUNT(DISTINCT visitor_id) AS uv FROM dwd_traffic_visit_di GROUP BY visit_date) v
LEFT JOIN (SELECT order_date, COUNT(DISTINCT buyer_id) AS buyers FROM dwd_trade_order_detail_di GROUP BY order_date) o
  ON o.order_date = v.visit_date""", []),
    ("ads_user_repurchase_yearly", "ADS-年度复购率", """
CREATE TABLE ads_user_repurchase_yearly (stat_year SMALLINT NOT NULL COMMENT '统计年份', buyer_cnt INT COMMENT '支付买家数',
  repurchase_buyer_cnt INT COMMENT '支付订单数≥2的买家数', repurchase_rate DECIMAL(8,4) COMMENT '复购率', PRIMARY KEY (stat_year))""", """
INSERT INTO ads_user_repurchase_yearly SELECT y, COUNT(*), SUM(cnt >= 2), ROUND(SUM(cnt >= 2) / COUNT(*), 4)
FROM (SELECT YEAR(order_time) AS y, buyer_id, COUNT(*) AS cnt FROM ods_trade_order_di WHERE pay_time IS NOT NULL
      GROUP BY YEAR(order_time), buyer_id) t GROUP BY y""", []),
    ("ads_coupon_usage_monthly", "ADS-优惠券月度使用情况", """
CREATE TABLE ads_coupon_usage_monthly (stat_month CHAR(7) NOT NULL COMMENT '统计月份（按领取月）',
  coupon_type VARCHAR(20) NOT NULL COMMENT '优惠券类型', receive_cnt INT COMMENT '领取数', used_cnt INT COMMENT '使用数',
  use_rate DECIMAL(8,4) COMMENT '核销率', PRIMARY KEY (stat_month, coupon_type))""", """
INSERT INTO ads_coupon_usage_monthly SELECT DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type, COUNT(*),
  SUM(r.use_status = 'used'), ROUND(SUM(r.use_status = 'used') / COUNT(*), 4)
FROM ods_mkt_coupon_receive_di r JOIN dim_coupon c ON c.coupon_id = r.coupon_id
GROUP BY DATE_FORMAT(r.receive_time, '%Y-%m'), c.coupon_type""", []),
]


def _dws_tables(rules):
    """按配置展开 DWS 模板，返回 [(表名, 注释, DDL, INSERT, 对账列表)]。"""
    result = []
    for event_key, grains in rules["dws"].items():
        domain, event, source, date_col, cn_event, measures = EVENTS[event_key]
        for grain in grains:
            gcols = GRAINS[grain]
            for period in rules["periods"]:
                expr, date_comment, cn_period = PERIODS[period]
                name = f"dws_{domain}_{grain}_{event}_{period}"
                cols = [f"stat_date DATE NOT NULL COMMENT '{date_comment}'"]
                cols += [f"{c} {t} COMMENT '{cm}'" for c, t, cm in gcols]
                cols += [f"{c} {t} COMMENT '{cm}'" for c, _, t, cm, _ in measures]
                pk = ", ".join(["stat_date"] + [gcols[0][0]])
                ddl = (f"CREATE TABLE {name} ({', '.join(cols)}, KEY idx_stat ({pk}))")
                date_expr = expr.format(d=date_col)
                group = ", ".join([date_expr] + [c for c, _, _ in gcols])
                select = ", ".join([date_expr] + [c for c, _, _ in gcols] + [e for _, e, _, _, _ in measures])
                insert = f"INSERT INTO {name} SELECT {select} FROM {source} GROUP BY {group}"
                checks = [(m, source, e) for m, e, _, _, additive in measures if additive]
                comment = f"DWS-{gcols[0][2].replace('ID', '')}粒度{cn_event}汇总（{cn_period}）"
                result.append((name, comment, ddl, insert, checks))
    return result


def run(cfg, conn):
    rules = cfg["layers"].get("rules")
    if not rules:
        print("  layer rules disabled")
        return
    tables = [(n, c, d, i, []) for n, c, d, i in FIXED_TABLES] + _dws_tables(rules) + ADS_TABLES
    manifest = []
    # 已存在的同名表（M0 手写的 dws_trade_user_order_1d 等）保留原定义，不覆盖，M0 题集的替代写法因此仍然成立
    existing = set(db.list_tables(conn, cfg["database"]))
    tables = [t for t in tables if t[0] not in existing]
    for name, comment, ddl, insert, checks in tables:
        db.execute(conn, f"DROP TABLE IF EXISTS `{name}`")
        db.execute(conn, ddl + f" ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='{comment}'")
        db.execute(conn, insert)
        for measure, source, expr in checks:
            manifest.append({
                "name": f"{name}.{measure} total = {source}",
                "sql": f"SELECT (SELECT SUM({measure}) FROM {name}), (SELECT {expr} FROM {source})",
            })
    out = Path(cfg["out_dir"]) / "layer_manifest.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"tables": [t[0] for t in tables], "checks": manifest}, ensure_ascii=False, indent=1),
                   encoding="utf-8")
    print(f"  rule-derived tables: {len(tables)}, reconciliation checks: {len(manifest)}")
