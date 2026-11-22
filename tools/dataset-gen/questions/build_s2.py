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

"""生成 S2 题集 questions/s2.jsonl：M0 的 10 道 + 新增题目。

题目由 AI 起草，必须通过 validate（标准 SQL 可执行且结果非空、替代写法结果一致）和 Java QuestionLoader
（人工标注的 tables 与解析 gold_sql 得到的表一致）两道校验。运行：python questions/build_s2.py
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
Y = lambda col: f"{col} >= '2025-01-01' AND {col} < '2026-01-01'"  # noqa: E731
PAID_GMV = ("SELECT SUM(i.item_amount) FROM trade_order o JOIN trade_order_item i ON i.order_id = o.order_id "
            "WHERE o.pay_time IS NOT NULL AND ")


def q(qid, question, gold, tables, difficulty, tags, evidence="", alts=(), order=False):
    return {"id": qid, "question": question, "evidence": evidence, "gold_sql": gold, "alt_sqls": list(alts),
            "tables": tables, "columns": [], "difficulty": difficulty, "tags": tags, "order_matters": order}


NEW = [
    # ---------- 支付 ----------
    q("s2-001", "2025年用支付宝支付的订单有多少笔？",
      f"SELECT COUNT(*) FROM pay_order WHERE pay_channel = 'alipay' AND {Y('pay_time')}",
      ["pay_order"], "L1", ["value_match"],
      alts=["SELECT SUM(pay_cnt) FROM ads_pay_channel_monthly WHERE pay_channel = 'alipay' "
            "AND stat_month BETWEEN '2025-01' AND '2025-12'"]),
    q("s2-002", "2025年各支付渠道的支付金额分别是多少？按金额从高到低排",
      f"SELECT pay_channel, SUM(pay_amt) AS amt FROM pay_order WHERE {Y('pay_time')} GROUP BY pay_channel "
      "ORDER BY amt DESC", ["pay_order"], "L2", ["ambiguous_table"], order=True,
      alts=["SELECT pay_channel, SUM(pay_amt) AS amt FROM ads_pay_channel_monthly WHERE stat_month "
            "BETWEEN '2025-01' AND '2025-12' GROUP BY pay_channel ORDER BY amt DESC"]),
    q("s2-003", "2025年用银行卡支付的平均每笔金额是多少？",
      f"SELECT ROUND(AVG(pay_amt), 2) FROM pay_order WHERE pay_channel = 'card' AND {Y('pay_time')}",
      ["pay_order"], "L1", ["value_match"]),
    q("s2-004", "2025年实付金额超过5000元的订单有多少笔？",
      f"SELECT COUNT(*) FROM trade_order WHERE pay_amount > 5000 AND pay_time IS NOT NULL AND {Y('order_time')}",
      ["trade_order"], "L1", ["metric"]),
    q("s2-005", "2025年12月25日到31日，每天的支付金额是多少？",
      "SELECT DATE(pay_time) AS d, SUM(pay_amt) FROM pay_order WHERE pay_time >= '2025-12-25' "
      "AND pay_time < '2026-01-01' GROUP BY DATE(pay_time) ORDER BY d",
      ["pay_order"], "L2", ["time_reasoning", "ambiguous_table"], order=True),
    # ---------- 退款 ----------
    q("s2-006", "2025年完成的退款，总金额是多少？",
      f"SELECT SUM(refund_amount) FROM pay_refund WHERE {Y('finish_time')}", ["pay_refund"], "L1",
      ["metric", "time_reasoning"], evidence="按退款完成时间统计",
      alts=["SELECT SUM(refund_amount) FROM dwd_trade_refund_di WHERE refund_date BETWEEN '2025-01-01' AND '2025-12-31'"]),
    q("s2-007", "因为质量问题退款的订单有多少笔？",
      "SELECT COUNT(*) FROM pay_refund WHERE refund_reason = 'quality'", ["pay_refund"], "L1", ["value_match"]),
    q("s2-008", "2025年每个月的退款率是多少？",
      "SELECT DATE_FORMAT(order_time, '%Y-%m') AS m, ROUND(SUM(order_status = 'refunded') / COUNT(*), 4) "
      f"FROM trade_order WHERE pay_time IS NOT NULL AND {Y('order_time')} GROUP BY m ORDER BY m",
      ["trade_order"], "L3", ["metric", "ambiguous_table"], order=True,
      evidence="退款率 = 已退款订单数 / 已支付订单数，按下单月份统计，保留4位小数",
      alts=["SELECT stat_month, refund_rate FROM ads_trade_refund_rate_monthly ORDER BY stat_month"]),
    q("s2-009", "退款金额最高的5个订单，买家分别是谁？",
      "SELECT u.user_name, r.refund_amount FROM pay_refund r JOIN trade_order o ON o.order_id = r.order_id "
      "JOIN user_info u ON u.user_id = o.buyer_id ORDER BY r.refund_amount DESC, r.order_id LIMIT 5",
      ["pay_refund", "trade_order", "user_info"], "L2", ["multi_hop"], order=True),
    q("s2-010", "退款订单最多的收货省份是哪个？",
      "SELECT o.receiver_province, COUNT(*) AS cnt FROM pay_refund r JOIN trade_order o ON o.order_id = r.order_id "
      "GROUP BY o.receiver_province ORDER BY cnt DESC, o.receiver_province LIMIT 1",
      ["pay_refund", "trade_order"], "L2", ["ambiguous_table"], order=True,
      alts=["SELECT receiver_province, COUNT(*) AS cnt FROM trade_order WHERE order_status = 'refunded' "
            "GROUP BY receiver_province ORDER BY cnt DESC, receiver_province LIMIT 1"]),
    q("s2-011", "pay_refund 表里退款原因为 late_delivery 的退款总金额是多少？",
      "SELECT SUM(refund_amount) FROM pay_refund WHERE refund_reason = 'late_delivery'", ["pay_refund"], "L1",
      ["explicit_name", "value_match"]),
    # ---------- 物流 ----------
    q("s2-012", "顺丰承运的订单，平均配送多少天（从发货到签收）？",
      "SELECT ROUND(AVG(TIMESTAMPDIFF(HOUR, ship_time, sign_time)) / 24, 2) FROM lgs_shipment "
      "WHERE carrier = 'sf' AND sign_time IS NOT NULL", ["lgs_shipment"], "L2", ["value_match", "metric"]),
    q("s2-013", "华东大区的仓库一共发了多少单货？",
      "SELECT COUNT(*) FROM lgs_shipment s JOIN lgs_warehouse w ON w.warehouse_id = s.warehouse_id "
      "WHERE w.region = '华东'", ["lgs_shipment", "lgs_warehouse"], "L2", ["value_match", "multi_hop"]),
    q("s2-014", "2025年各大区仓库，从支付到发货平均要多少小时？",
      "SELECT w.region, ROUND(AVG(TIMESTAMPDIFF(HOUR, o.pay_time, s.ship_time)), 2) FROM lgs_shipment s "
      "JOIN lgs_warehouse w ON w.warehouse_id = s.warehouse_id JOIN trade_order o ON o.order_id = s.ord_no "
      f"WHERE {Y('s.ship_time')} GROUP BY w.region ORDER BY w.region",
      ["lgs_shipment", "lgs_warehouse", "trade_order"], "L3", ["multi_hop", "metric"], order=True,
      alts=["SELECT warehouse_region, ROUND(AVG(ship_hours), 2) FROM dwd_lgs_shipment_di WHERE ship_date "
            "BETWEEN '2025-01-01' AND '2025-12-31' GROUP BY warehouse_region ORDER BY warehouse_region"]),
    q("s2-015", "现在还有多少订单已经发货但没有签收？",
      "SELECT COUNT(*) FROM lgs_shipment WHERE sign_time IS NULL", ["lgs_shipment"], "L1", ["ambiguous_table"],
      alts=["SELECT COUNT(*) FROM trade_order WHERE order_status = 'shipped'"]),
    q("s2-016", "每个仓库分别发了多少单？显示仓库名称",
      "SELECT w.warehouse_name, COUNT(*) AS cnt FROM lgs_shipment s JOIN lgs_warehouse w "
      "ON w.warehouse_id = s.warehouse_id GROUP BY w.warehouse_name ORDER BY cnt DESC, w.warehouse_name",
      ["lgs_shipment", "lgs_warehouse"], "L2", ["multi_hop"], order=True),
    q("s2-017", "lgs_shipment 里 ord_no 对应的已完成订单，平均实付金额是多少？",
      "SELECT ROUND(AVG(o.pay_amount), 2) FROM lgs_shipment s JOIN trade_order o ON o.order_id = s.ord_no "
      "WHERE o.order_status = 'completed'", ["lgs_shipment", "trade_order"], "L2", ["explicit_name", "multi_hop"]),
    q("s2-018", "从支付到发货，平均要等多少小时？",
      "SELECT ROUND(AVG(TIMESTAMPDIFF(HOUR, o.pay_time, s.ship_time)), 2) FROM lgs_shipment s "
      "JOIN trade_order o ON o.order_id = s.ord_no", ["lgs_shipment", "trade_order"], "L2", ["metric"],
      alts=["SELECT ROUND(AVG(ship_hours), 2) FROM dwd_lgs_shipment_di"]),
    # ---------- 评价 ----------
    q("s2-019", "评分为1星的评价有多少条？", "SELECT COUNT(*) FROM item_review WHERE rating = 1", ["item_review"],
      "L1", ["value_match"]),
    q("s2-020", "2025年各一级品类商品的平均评分是多少？",
      "SELECT c1.category_name, ROUND(AVG(r.rating), 2) FROM item_review r JOIN item_sku s ON s.sku_id = r.sku_id "
      "JOIN item_category c2 ON c2.category_id = s.category_id JOIN item_category c1 ON c1.category_id = c2.parent_id "
      f"WHERE {Y('r.review_time')} GROUP BY c1.category_name ORDER BY c1.category_name",
      ["item_review", "item_sku", "item_category"], "L3", ["multi_hop", "metric"], order=True,
      evidence="按评价时间统计，保留两位小数",
      alts=["SELECT category1_name, ROUND(AVG(rating), 2) FROM dwd_item_review_di WHERE review_date BETWEEN "
            "'2025-01-01' AND '2025-12-31' GROUP BY category1_name ORDER BY category1_name"]),
    q("s2-021", "带图评价占全部评价的百分比是多少？",
      "SELECT ROUND(SUM(has_image) / COUNT(*) * 100, 2) FROM item_review", ["item_review"], "L2", ["metric"]),
    q("s2-022", "评价数不少于100条的品牌里，好评率最高的是哪个？",
      "SELECT s.brand_name, ROUND(SUM(r.rating >= 4) / COUNT(*), 4) AS rate FROM item_review r "
      "JOIN item_sku s ON s.sku_id = r.sku_id GROUP BY s.brand_name HAVING COUNT(*) >= 100 "
      "ORDER BY rate DESC, s.brand_name LIMIT 1", ["item_review", "item_sku"], "L3", ["metric"], order=True,
      evidence="好评 = 评分4星及以上；好评率 = 好评数 / 评价数"),
    q("s2-023", "item_review 中每个评分各有多少条评价？",
      "SELECT rating, COUNT(*) FROM item_review GROUP BY rating ORDER BY rating", ["item_review"], "L1",
      ["explicit_name"], order=True),
    q("s2-024", "评价数最多的商品是哪个？它的平均评分是多少？",
      "SELECT s.sku_name, COUNT(*) AS cnt, ROUND(AVG(r.rating), 2) FROM item_review r JOIN item_sku s "
      "ON s.sku_id = r.sku_id GROUP BY s.sku_id, s.sku_name ORDER BY cnt DESC, s.sku_id LIMIT 1",
      ["item_review", "item_sku"], "L2", ["multi_hop"], order=True),
    # ---------- 售后 ----------
    q("s2-025", "2025年申请的售后里，退货类型的有多少单？",
      f"SELECT COUNT(*) FROM aftersale_order WHERE aftersale_type = 'return' AND {Y('apply_time')}",
      ["aftersale_order"], "L1", ["value_match", "time_reasoning"]),
    q("s2-026", "售后被驳回次数最多的二级品类是哪个？",
      "SELECT c.category_name, COUNT(*) AS cnt FROM aftersale_order a JOIN trade_order_item i ON i.id = a.order_item_id "
      "JOIN item_sku s ON s.sku_id = i.sku_id JOIN item_category c ON c.category_id = s.category_id "
      "WHERE a.status = 'rejected' GROUP BY c.category_name ORDER BY cnt DESC, c.category_name LIMIT 1",
      ["aftersale_order", "trade_order_item", "item_sku", "item_category"], "L3", ["multi_hop", "value_match"],
      order=True),
    q("s2-027", "aftersale_order 里状态为 processing 的售后单有多少？",
      "SELECT COUNT(*) FROM aftersale_order WHERE status = 'processing'", ["aftersale_order"], "L1",
      ["explicit_name", "value_match"]),
    q("s2-028", "从签收到申请售后，平均间隔多少天？",
      "SELECT ROUND(AVG(TIMESTAMPDIFF(HOUR, s.sign_time, a.apply_time)) / 24, 2) FROM aftersale_order a "
      "JOIN trade_order_item i ON i.id = a.order_item_id JOIN lgs_shipment s ON s.ord_no = i.order_id",
      ["aftersale_order", "trade_order_item", "lgs_shipment"], "L3", ["multi_hop", "metric"]),
    # ---------- 访问 ----------
    q("s2-029", "2025年11月商品页的浏览量（PV）是多少？",
      "SELECT COUNT(*) FROM log_visit WHERE visit_time >= '2025-11-01' AND visit_time < '2025-12-01'",
      ["log_visit"], "L1", ["time_reasoning", "ambiguous_table"],
      alts=["SELECT COUNT(*) FROM dwd_traffic_visit_di WHERE visit_date BETWEEN '2025-11-01' AND '2025-11-30'"]),
    q("s2-030", "2025年小程序渠道的访客数（UV）是多少？",
      f"SELECT COUNT(DISTINCT visitor_id) FROM log_visit WHERE channel = 'mini_program' AND {Y('visit_time')}",
      ["log_visit"], "L1", ["value_match"]),
    q("s2-031", "2025年全年的访问转化率是多少？",
      f"SELECT ROUND((SELECT COUNT(DISTINCT buyer_id) FROM trade_order WHERE {Y('order_time')}) / "
      f"(SELECT COUNT(DISTINCT visitor_id) FROM log_visit WHERE {Y('visit_time')}), 4)",
      ["trade_order", "log_visit"], "L4", ["metric"],
      evidence="访问转化率 = 下单用户数 / 访客数（按去重用户），保留4位小数"),
    q("s2-032", "被浏览次数最多的10个商品是哪些？",
      "SELECT s.sku_name, COUNT(*) AS pv FROM log_visit v JOIN item_sku s ON s.sku_id = v.sku_id "
      "GROUP BY s.sku_id, s.sku_name ORDER BY pv DESC, s.sku_id LIMIT 10",
      ["log_visit", "item_sku"], "L2", ["ambiguous_table", "multi_hop"], order=True),
    q("s2-033", "哪个访问渠道的平均页面停留时间最长？",
      "SELECT channel, ROUND(AVG(stay_seconds), 1) AS s FROM log_visit GROUP BY channel ORDER BY s DESC, channel LIMIT 1",
      ["log_visit"], "L2", ["metric"], order=True),
    q("s2-034", "log_visit 表中2025年12月31日一共有多少次访问？",
      "SELECT COUNT(*) FROM log_visit WHERE visit_time >= '2025-12-31' AND visit_time < '2026-01-01'",
      ["log_visit"], "L1", ["explicit_name", "ambiguous_table", "time_reasoning"]),
    q("s2-035", "平均每个访客访问了多少次商品页？",
      "SELECT ROUND(COUNT(*) / COUNT(DISTINCT visitor_id), 2) FROM log_visit", ["log_visit"], "L2", ["metric"]),
    # ---------- 营销 ----------
    q("s2-036", "每种类型优惠券的核销率分别是多少？",
      "SELECT c.coupon_type, ROUND(SUM(r.use_status = 'used') / COUNT(*), 4) FROM mkt_coupon_receive r "
      "JOIN mkt_coupon c ON c.coupon_id = r.coupon_id GROUP BY c.coupon_type ORDER BY c.coupon_type",
      ["mkt_coupon_receive", "mkt_coupon"], "L2", ["metric", "multi_hop"], order=True,
      evidence="核销率 = 已使用的领券记录数 / 领券记录数，保留4位小数",
      alts=["SELECT coupon_type, ROUND(SUM(used_cnt) / SUM(receive_cnt), 4) FROM ads_coupon_usage_monthly "
            "GROUP BY coupon_type ORDER BY coupon_type"]),
    q("s2-037", "领过优惠券、但2025年从来没有下过单的用户有多少？",
      "SELECT COUNT(DISTINCT r.user_id) FROM mkt_coupon_receive r WHERE r.user_id NOT IN "
      f"(SELECT buyer_id FROM trade_order WHERE {Y('order_time')})",
      ["mkt_coupon_receive", "trade_order"], "L3", ["multi_hop"]),
    q("s2-038", "用了优惠券的订单，平均优惠了多少钱？",
      "SELECT ROUND(AVG(discount_amt), 2) FROM trade_order WHERE discount_amt > 0", ["trade_order"], "L2",
      ["ambiguous_table"],
      alts=["SELECT ROUND(AVG(o.discount_amt), 2) FROM trade_order o JOIN mkt_coupon_receive r "
            "ON r.used_order_id = o.order_id"]),
    q("s2-039", "满减券一共被使用了多少次？",
      "SELECT COUNT(*) FROM mkt_coupon_receive r JOIN mkt_coupon c ON c.coupon_id = r.coupon_id "
      "WHERE c.coupon_type = 'full_reduction' AND r.use_status = 'used'",
      ["mkt_coupon_receive", "mkt_coupon"], "L2", ["value_match"]),
    q("s2-040", "统计 mkt_coupon 中每种 coupon_type 各有多少张券",
      "SELECT coupon_type, COUNT(*) FROM mkt_coupon GROUP BY coupon_type ORDER BY coupon_type", ["mkt_coupon"],
      "L1", ["explicit_name"], order=True),
    # ---------- 用户与地址 ----------
    q("s2-041", "有多少用户设置了2个及以上的收货地址？",
      "SELECT COUNT(*) FROM (SELECT user_id FROM user_address GROUP BY user_id HAVING COUNT(*) >= 2) t",
      ["user_address"], "L2", ["metric"]),
    q("s2-042", "默认收货地址在广东的用户有多少个？",
      "SELECT COUNT(*) FROM user_address WHERE is_default = 1 AND province = '广东省'", ["user_address"], "L1",
      ["value_match"]),
    q("s2-043", "user_address 中标签为 company 的地址有多少个？",
      "SELECT COUNT(*) FROM user_address WHERE address_tag = 'company'", ["user_address"], "L1",
      ["explicit_name", "value_match"]),
    q("s2-044", "2025年新注册了多少用户？", f"SELECT COUNT(*) FROM user_info WHERE {Y('register_time')}",
      ["user_info"], "L1", ["time_reasoning"]),
    q("s2-045", "2025年每个月新注册的用户数是多少？",
      f"SELECT DATE_FORMAT(register_time, '%Y-%m') AS m, COUNT(*) FROM user_info WHERE {Y('register_time')} "
      "GROUP BY m ORDER BY m", ["user_info"], "L2", ["time_reasoning"], order=True),
    q("s2-046", "注册当月就下了单的用户有多少？",
      "SELECT COUNT(DISTINCT u.user_id) FROM user_info u JOIN trade_order o ON o.buyer_id = u.user_id "
      "WHERE DATE_FORMAT(o.order_time, '%Y-%m') = DATE_FORMAT(u.register_time, '%Y-%m')",
      ["user_info", "trade_order"], "L3", ["time_reasoning"]),
    q("s2-047", "90后用户2025年的客单价是多少？",
      "SELECT ROUND(SUM(o.pay_amount) / COUNT(*), 2) FROM trade_order o JOIN user_info u ON u.user_id = o.buyer_id "
      f"WHERE u.birth_year BETWEEN 1990 AND 1999 AND o.pay_time IS NOT NULL AND {Y('o.order_time')}",
      ["trade_order", "user_info"], "L3", ["metric", "value_match"],
      evidence="客单价 = 已支付订单实付金额之和 / 已支付订单数；90后 = 1990～1999年出生"),
    # ---------- 品牌与品类 ----------
    q("s2-048", "S级品牌的商品在2025年一共卖了多少钱？",
      "SELECT SUM(i.item_amount) FROM trade_order o JOIN trade_order_item i ON i.order_id = o.order_id "
      "JOIN item_sku s ON s.sku_id = i.sku_id JOIN item_brand b ON b.brand_name = s.brand_name "
      f"WHERE b.brand_level = 'S' AND o.pay_time IS NOT NULL AND {Y('o.order_time')}",
      ["trade_order", "trade_order_item", "item_sku", "item_brand"], "L3", ["multi_hop", "value_match"],
      evidence="销售额 = 已支付订单的明细金额之和"),
    q("s2-049", "来自日本的品牌一共有多少个在售商品？",
      "SELECT COUNT(*) FROM item_sku s JOIN item_brand b ON b.brand_name = s.brand_name "
      "WHERE b.origin_country = '日本' AND s.sku_status = 'on_sale'", ["item_sku", "item_brand"], "L2",
      ["value_match", "multi_hop"]),
    q("s2-050", "2025年销售额最高的二级品类是哪个？",
      "SELECT c.category_name, SUM(i.item_amount) AS amt FROM trade_order o JOIN trade_order_item i "
      "ON i.order_id = o.order_id JOIN item_sku s ON s.sku_id = i.sku_id JOIN item_category c "
      f"ON c.category_id = s.category_id WHERE o.pay_time IS NOT NULL AND {Y('o.order_time')} "
      "GROUP BY c.category_name ORDER BY amt DESC, c.category_name LIMIT 1",
      ["trade_order", "trade_order_item", "item_sku", "item_category"], "L3", ["multi_hop", "metric"], order=True,
      alts=["SELECT category2_name, SUM(item_amount) AS amt FROM dwd_trade_order_detail_di WHERE is_paid = 1 "
            "AND order_date BETWEEN '2025-01-01' AND '2025-12-31' GROUP BY category2_name "
            "ORDER BY amt DESC, category2_name LIMIT 1"]),
    # ---------- 交易与时间 ----------
    q("s2-051", "2025年每个会员等级贡献了多少GMV？",
      "SELECT u.member_level, SUM(i.item_amount) FROM trade_order o JOIN trade_order_item i ON i.order_id = o.order_id "
      f"JOIN user_info u ON u.user_id = o.buyer_id WHERE o.pay_time IS NOT NULL AND {Y('o.order_time')} "
      "GROUP BY u.member_level ORDER BY u.member_level", ["trade_order", "trade_order_item", "user_info"], "L3",
      ["metric", "multi_hop"], order=True, evidence="GMV = 已支付订单的明细金额之和",
      alts=["SELECT member_level, SUM(IF(is_paid = 1, item_amount, 0)) FROM dwd_trade_order_detail_di WHERE "
            "order_date BETWEEN '2025-01-01' AND '2025-12-31' GROUP BY member_level ORDER BY member_level"]),
    q("s2-052", "2025年第二季度的GMV是多少？",
      PAID_GMV + "o.order_time >= '2025-04-01' AND o.order_time < '2025-07-01'",
      ["trade_order", "trade_order_item"], "L2", ["time_reasoning", "metric"],
      evidence="GMV = 已支付订单的明细金额之和，按下单时间",
      alts=["SELECT SUM(gmv) FROM ads_trade_gmv_monthly WHERE stat_month IN ('2025-04', '2025-05', '2025-06')",
            "SELECT SUM(gmv) FROM ads_trade_gmv_daily WHERE stat_date BETWEEN '2025-04-01' AND '2025-06-30'"]),
    q("s2-053", "2025年上半年每个月有多少笔支付订单？",
      "SELECT DATE_FORMAT(order_time, '%Y-%m') AS m, COUNT(*) FROM trade_order WHERE pay_time IS NOT NULL "
      "AND order_time >= '2025-01-01' AND order_time < '2025-07-01' GROUP BY m ORDER BY m", ["trade_order"], "L2",
      ["time_reasoning", "ambiguous_table"], order=True, evidence="按下单月份统计",
      alts=["SELECT stat_month, paid_order_cnt FROM ads_trade_gmv_monthly WHERE stat_month BETWEEN '2025-01' "
            "AND '2025-06' ORDER BY stat_month"]),
    q("s2-054", "2025年国庆假期（10月1日到7日）一共有多少订单？",
      "SELECT COUNT(*) FROM trade_order WHERE order_time >= '2025-10-01' AND order_time < '2025-10-08'",
      ["trade_order"], "L2", ["time_reasoning"]),
    q("s2-055", "2025年周末下的订单占全年订单的百分比是多少？",
      "SELECT ROUND(SUM(DAYOFWEEK(order_time) IN (1, 7)) / COUNT(*) * 100, 2) FROM trade_order "
      f"WHERE {Y('order_time')}", ["trade_order"], "L3", ["time_reasoning", "metric"]),
    q("s2-056", "2025年新注册用户贡献了多少GMV？",
      PAID_GMV.replace("JOIN trade_order_item i ON i.order_id = o.order_id ",
                       "JOIN trade_order_item i ON i.order_id = o.order_id JOIN user_info u ON u.user_id = o.buyer_id ")
      + f"{Y('o.order_time')} AND {Y('u.register_time')}",
      ["trade_order", "trade_order_item", "user_info"], "L4", ["multi_hop", "metric", "time_reasoning"],
      evidence="GMV = 已支付订单的明细金额之和"),
    q("s2-057", "2025年12月的GMV比11月增长了百分之多少？",
      "SELECT ROUND((d.v - n.v) / n.v * 100, 2) FROM (" + PAID_GMV.replace("SELECT SUM(i.item_amount)",
      "SELECT SUM(i.item_amount) AS v") + "o.order_time >= '2025-12-01' AND o.order_time < '2026-01-01') d, ("
      + PAID_GMV.replace("SELECT SUM(i.item_amount)", "SELECT SUM(i.item_amount) AS v")
      + "o.order_time >= '2025-11-01' AND o.order_time < '2025-12-01') n",
      ["trade_order", "trade_order_item"], "L4", ["time_reasoning", "metric"],
      evidence="环比增长率 = (本月GMV - 上月GMV) / 上月GMV × 100，保留两位小数",
      alts=["SELECT ROUND((d.gmv - n.gmv) / n.gmv * 100, 2) FROM ads_trade_gmv_monthly d, ads_trade_gmv_monthly n "
            "WHERE d.stat_month = '2025-12' AND n.stat_month = '2025-11'"]),
    q("s2-058", "2025年销售额排名前5的收货省份是哪些？",
      "SELECT o.receiver_province, SUM(i.item_amount) AS amt FROM trade_order o JOIN trade_order_item i "
      f"ON i.order_id = o.order_id WHERE o.pay_time IS NOT NULL AND {Y('o.order_time')} "
      "GROUP BY o.receiver_province ORDER BY amt DESC, o.receiver_province LIMIT 5",
      ["trade_order", "trade_order_item"], "L2", ["metric", "ambiguous_table"], order=True,
      alts=["SELECT receiver_province, SUM(sales_amount) AS amt FROM ads_trade_province_sales_monthly WHERE "
            "stat_month BETWEEN '2025-01' AND '2025-12' GROUP BY receiver_province ORDER BY amt DESC, "
            "receiver_province LIMIT 5"]),
    q("s2-059", "2025年各会员等级的复购率分别是多少？",
      "SELECT t.member_level, ROUND(SUM(t.cnt >= 2) / COUNT(*) * 100, 2) FROM (SELECT u.member_level, o.buyer_id, "
      "COUNT(*) AS cnt FROM trade_order o JOIN user_info u ON u.user_id = o.buyer_id WHERE o.pay_time IS NOT NULL "
      f"AND {Y('o.order_time')} GROUP BY u.member_level, o.buyer_id) t GROUP BY t.member_level ORDER BY t.member_level",
      ["trade_order", "user_info"], "L4", ["metric", "multi_hop"], order=True,
      evidence="复购率 = 已支付订单数 ≥ 2 的用户数 / 已支付订单数 ≥ 1 的用户数 × 100，保留两位小数"),
    # ---------- 直接点名表 / 列 ----------
    q("s2-060", "pay_order 表里一共有多少条记录？", "SELECT COUNT(*) FROM pay_order", ["pay_order"], "L1",
      ["explicit_name"]),
    q("s2-061", "trade_order_item 中购买件数（qty）为3的明细有多少条？",
      "SELECT COUNT(*) FROM trade_order_item WHERE qty = 3", ["trade_order_item"], "L1", ["explicit_name"]),
    q("s2-062", "lgs_warehouse 里每个仓库在哪个省份？",
      "SELECT warehouse_name, province FROM lgs_warehouse ORDER BY warehouse_id", ["lgs_warehouse"], "L1",
      ["explicit_name"], order=True),
]


def main():
    m0 = [json.loads(line) for line in (HERE / "m0.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    rows = m0 + NEW
    # 数仓分层表上的替代写法（已在三档数据上执行验证），见 layered_alts.json
    layered = json.loads((HERE / "layered_alts.json").read_text(encoding="utf-8"))["alts"]
    for r in rows:
        for alt in layered.get(r["id"], []):
            if alt not in r["alt_sqls"]:
                r["alt_sqls"] = r["alt_sqls"] + [alt]
    ids = [r["id"] for r in rows]
    assert len(ids) == len(set(ids)), "duplicate ids"
    with open(HERE / "s2.jsonl", "w", encoding="utf-8", newline="\n") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    tags = {}
    for r in rows:
        for t in r["tags"]:
            tags[t] = tags.get(t, 0) + 1
    diff = {}
    for r in rows:
        diff[r["difficulty"]] = diff.get(r["difficulty"], 0) + 1
    print(f"{len(rows)} questions; tags {dict(sorted(tags.items()))}; difficulty {dict(sorted(diff.items()))}")


if __name__ == "__main__":
    main()
