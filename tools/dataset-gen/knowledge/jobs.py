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

"""把事实组装成一个个写作任务（job）。每个 job 由大模型写成一篇文档或一条 QA / FAQ / 业务知识。

job 字段：id、kind、output（md / json）、title、sections（md 必须按序出现的二级标题）、facts（交给大模型的事实）、
required（正文里必须原样出现的字符串）、min_chars / max_chars、allow_md_table、meta（写进 manifest 的元数据）。
"""
import hashlib
import random
import re
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

import yaml

from gen import layer_rules
from .facts import Facts, layer_of

TOPICS = yaml.safe_load((Path(__file__).parent / "topics.yaml").read_text(encoding="utf-8"))
YEAR = 2025
STYLES = [
    "条理清晰，偏要点式，句子简短",
    "叙述式，像资深同事写给新同事的说明",
    "在关键处点出一两个容易误用的地方，其余直接陈述",
    "正式文档风格，用语严谨",
    "务实简练，多举具体的取数例子",
]
# 报告、公告、事故、需求单这类"记录型"文档用另一组风格
RECORD_STYLES = [
    "简洁的数据播报风格",
    "偏分析的叙述风格",
    "面向管理层的要点式摘要",
    "正式公文风格",
    "团队内部沟通的平实口吻",
]
RECORD_KINDS = {"monthly", "weekly", "change", "incident", "ticket"}
TEAMS = ["交易数据组", "用户数据组", "商品数据组", "物流数据组", "流量数据组", "数据平台组"]
DEPTS = ["运营部", "商品部", "市场部", "财务部", "物流部", "客服部", "管理层", "会员中心"]


def rng_for(key):
    return random.Random(int(hashlib.md5(key.encode("utf-8")).hexdigest()[:8], 16))


def style_for(job_id, kind=None):
    styles = RECORD_STYLES if kind in RECORD_KINDS else STYLES
    return styles[rng_for(job_id).randrange(len(styles))]


def money(v):
    return f"{float(v):,.2f} 元"


def num(v):
    return f"{int(v):,}"


def pct(v):
    return f"{float(v) * 100:.2f}%"


def change(cur, prev):
    if not prev:
        return "无可比数据"
    r = (float(cur) - float(prev)) / float(prev)
    return f"{'+' if r >= 0 else ''}{r * 100:.1f}%"


def md_job(job_id, kind, title, sections, facts, required=(), min_chars=500, max_chars=3000, allow_md_table=False,
           meta=None):
    return {"id": job_id, "kind": kind, "output": "md", "title": title, "sections": list(sections), "facts": facts,
            "required": list(dict.fromkeys(required)), "min_chars": min_chars, "max_chars": max_chars,
            "allow_md_table": allow_md_table, "meta": meta or {}, "style": style_for(job_id, kind)}


def json_job(job_id, kind, fields, facts, required=(), meta=None):
    return {"id": job_id, "kind": kind, "output": "json", "fields": list(fields), "facts": facts,
            "required": list(required), "meta": meta or {}, "style": style_for(job_id)}


class JobBuilder:

    def __init__(self, facts: Facts):
        self.f = facts
        self.metrics = self._metrics()

    # ---------- 公共事实 ----------

    def table_brief(self, t):
        return {"表名": t, "分层": layer_of(t), "表注释": self.f.comment(t),
                "字段": [f"{c}（{typ}）：{cm}" for c, typ, cm in self.f.columns(t)]}

    def dws_families(self):
        fam = defaultdict(list)
        for t in self.f.effective:
            m = re.match(r"^(dws_.+)_(1d|1w|1m)$", t)
            if m:
                fam[m.group(1)].append(t)
        return {k: sorted(v, key=lambda x: ["1d", "1w", "1m"].index(x[-2:])) for k, v in sorted(fam.items())}

    def _metrics(self):
        """指标清单：DWS 度量 + ADS 新增指标 + 派生指标。"""
        metrics = {}
        for key, (_, event, source, date_col, cn_event, measures) in layer_rules.EVENTS.items():
            for col, expr, _, cm, additive in measures:
                metrics.setdefault(col, {"指标": col, "中文名": cm, "事件": cn_event, "来源明细表": source,
                                         "日期归属": date_col, "计算": expr, "可加和": additive})
        metrics["paid_order_cnt"] = {"指标": "paid_order_cnt", "中文名": "支付订单数", "事件": "下单",
                                     "来源明细表": "dwd_trade_order_detail_di", "日期归属": "order_date",
                                     "计算": "COUNT(DISTINCT IF(is_paid = 1, order_id, NULL))", "可加和": False}
        for name, _, ddl, insert, _ in layer_rules.ADS_TABLES:
            for col, cm in re.findall(r"\b([a-z_]+) [A-Z]+(?:\(\d+(?:,\d+)?\))?(?: NOT NULL)? COMMENT '([^']+)'", ddl):
                if col.startswith("stat_") or col in metrics or col in ("category1_name", "receiver_province",
                                                                      "pay_channel", "warehouse_region",
                                                                      "coupon_type"):
                    continue
                metrics[col] = {"指标": col, "中文名": cm, "事件": "报表", "来源报表": name,
                                "计算（报表加工 SQL）": insert.strip(), "可加和": False}
        derived = [
            ("客单价", "支付金额 / 支付订单数（或 GMV / 支付订单数），按需求说明分母", ["ads_trade_gmv_monthly"]),
            ("签收率", "signed_cnt / ship_cnt", ["dws_lgs_carrier_ship_1d"]),
            ("件单价", "paid_amount / item_qty", ["dws_trade_cate1_order_1d"]),
            ("人均浏览量", "pv / uv（uv 不可跨天相加）", ["dws_traffic_vchannel_visit_1d"]),
            ("平均停留时长", "stay_seconds / pv", ["dws_traffic_cate1_visit_1d"]),
            ("退款金额占比", "refund_amount / pay_amt（同一时间范围）", ["dws_trade_prov_refund_1d", "dws_trade_prov_pay_1d"]),
        ]
        for cn, formula, tables in derived:
            metrics[cn] = {"指标": cn, "中文名": cn, "事件": "派生", "计算": formula, "参考表": tables,
                           "可加和": False}
        for m in metrics.values():
            col = m["指标"]
            m["出现在"] = [t for t in self.f.effective if col in self.f.tables[t]["columns"]][:12]
        return metrics

    # ---------- 文档 ----------

    def table_pages(self):
        jobs = []
        for t in self.f.effective:
            cols = self.f.columns(t)
            facts = self.table_brief(t)
            facts["上游"] = self.f.upstream.get(t) or (["业务系统（在线数据库）"] if layer_of(t) == "CORE" else [])
            facts["下游"] = sorted(self.f.downstream.get(t, []))[:15]
            facts["行数"] = num(self.f.row_count(t))
            facts["同家族噪声表"] = self.f.family_summary(t)
            if t in self.f.sql and layer_of(t) != "ODS":
                facts["加工 SQL"] = self.f.sql[t]
            fks = [fk for fk in self.f.foreign_keys if t in (fk["table"], fk["ref_table"])]
            if fks:
                facts["关联关系"] = [f"{k['table']}.{k['column']} → {k['ref_table']}.{k['ref_column']}" for k in fks]
            jobs.append(md_job(f"doc-table-{t}", "table", f"表说明：{t}（{self.f.comment(t)}）",
                               ["概述", "字段", "上下游", "使用注意"], facts,
                               required=[t] + [c for c, _, _ in cols],
                               min_chars=500, max_chars=1200 + 120 * len(cols),
                               meta={"docType": "表说明", "tables": [t]}))
        return jobs

    def metric_cards(self, changes):
        jobs = []
        for key, m in self.metrics.items():
            facts = dict(m)
            facts["历史变更"] = [c for c in changes if c.get("指标") == key] or "上线以来未调整计算规则"
            jobs.append(md_job(f"doc-metric-{key}", "metric", f"指标说明：{m['中文名']}（{key}）",
                               ["计算公式", "取哪张表", "来源与历史变化"], facts, required=[key],
                               min_chars=400, max_chars=2200, meta={"docType": "指标说明", "metrics": [key]}))
        return jobs

    def etl_docs(self):
        jobs = []
        schedule = {"ODS": "01:00", "DIM": "02:00", "DWD": "02:30", "DWS": "03:30", "ADS": "05:00"}
        groups = []
        for t in self.f.effective:
            if layer_of(t) in ("ODS", "DIM", "DWD", "ADS"):
                groups.append((t, [t]))
        for fam, tables in self.dws_families().items():
            groups.append((fam, tables))
        for name, tables in groups:
            lay = layer_of(tables[0])
            facts = {"任务": name, "分层": lay, "产出表": [{"表名": t, "表注释": self.f.comment(t),
                                                     "行数": num(self.f.row_count(t))} for t in tables],
                     "加工 SQL": {t: self.f.sql.get(t, "") for t in tables},
                     "依赖": sorted({u for t in tables for u in self.f.upstream.get(t, [])}),
                     "调度": f"每日 {schedule[lay]} 开始，依赖上游全部成功后运行，处理前一天（T-1）的数据",
                     "质量检查": ["主键 / 粒度唯一", "关键字段非空", "行数与前一天相比波动超过 30% 告警"]
                     + (["可加和指标总额与来源明细对账（DWS 必须等于 DWD）"] if lay in ("DWS", "ADS") else [])}
            jobs.append(md_job(f"doc-etl-{name}", "etl", f"加工说明：{name}",
                               ["加工逻辑", "依赖与调度", "质量检查"], facts, required=tables,
                               min_chars=400, max_chars=2600, meta={"docType": "加工说明", "tables": tables}))
        return jobs

    def manuals(self):
        jobs = []
        facts_common = {
            "分层前缀": ["ods_", "dim_", "dwd_", "dws_", "ads_"],
            "DWS 命名": "dws_<域>_<粒度>_<事件>_<周期>，周期 1d / 1w / 1m，例如 dws_trade_prov_order_1d",
            "DWS 家族示例": list(self.dws_families())[:8],
            "ADS 示例": [t for t in self.f.effective if t.startswith("ads_")][:6],
            "噪声表类型与示例": {k: {"示例": v["示例"][:3], "张数": v["张数"]} for k, v in
                          self._noise_type_summary().items()},
            "有效表数": len(self.f.effective), "噪声表数": len(self.f.noise),
            "分区字段": "dt（分区日期）；业务日期另有 order_date / pay_date 等",
        }
        for m in TOPICS["manuals"]:
            job_id = f"doc-manual-{m['title']}"
            jobs.append(md_job(job_id, "manual", m["title"], m["chapters"], facts_common, min_chars=5000,
                               max_chars=14000, meta={"docType": "规范手册"}))
        return jobs

    def _noise_type_summary(self):
        out = defaultdict(lambda: {"张数": 0, "示例": []})
        for owner, kinds in self.f.families.items():
            for kind, names in kinds.items():
                out[kind]["张数"] += len(names)
                if len(out[kind]["示例"]) < 4:
                    out[kind]["示例"].append(sorted(names)[0])
        for k, v in self.f.orphan_noise_summary().items():
            out[k] = v
        return dict(out)

    # ---------- 月报 ----------

    def monthly_reports(self):
        q = self.f.query
        months = [f"{YEAR}-{m:02d}" for m in range(1, 13)]
        jobs = []

        def prev(m):
            i = months.index(m)
            return months[i - 1] if i else None

        def first_day(m):
            return f"{m}-01"

        topics = []

        def gmv(m):
            cur = q("SELECT gmv, paid_order_cnt, paid_buyer_cnt FROM ads_trade_gmv_monthly WHERE stat_month=%s", m)[0]
            p = prev(m)
            pv = q("SELECT gmv FROM ads_trade_gmv_monthly WHERE stat_month=%s", p)[0][0] if p else None
            peak = q("SELECT stat_date, gmv FROM ads_trade_gmv_daily WHERE DATE_FORMAT(stat_date,'%%Y-%%m')=%s "
                     "ORDER BY gmv DESC LIMIT 1", m)[0]
            f = {"GMV": money(cur[0]), "环比": change(cur[0], pv), "支付订单数": num(cur[1]), "支付买家数": num(cur[2]),
                 "客单价（GMV/支付订单数）": money(float(cur[0]) / cur[1]), "单日最高": f"{peak[0]}，{money(peak[1])}"}
            return f, [f["GMV"], f["支付订单数"]], ["ads_trade_gmv_monthly", "ads_trade_gmv_daily"]
        topics.append(("gmv", "交易 GMV 月报", gmv))

        def ranked(sql, m, label, fmt=money):
            rows = q(sql, m)
            return [f"{r[0]}：{fmt(r[1])}" for r in rows]

        def cate(m):
            rows = ranked("SELECT category1_name, sales_amount FROM ads_trade_cate1_sales_monthly WHERE stat_month=%s "
                          "ORDER BY sales_amount DESC", m, "")
            tot = q("SELECT SUM(sales_amount), SUM(item_qty) FROM ads_trade_cate1_sales_monthly WHERE stat_month=%s", m)[0]
            f = {"销售额合计": money(tot[0]), "销售件数": num(tot[1]), "各一级品类销售额（降序）": rows}
            return f, [f["销售额合计"], rows[0].split("：")[0]], ["ads_trade_cate1_sales_monthly"]
        topics.append(("cate1", "一级品类销售月报", cate))

        def prov(m):
            rows = ranked("SELECT receiver_province, sales_amount FROM ads_trade_province_sales_monthly "
                          "WHERE stat_month=%s ORDER BY sales_amount DESC", m, "")
            f = {"省份数": len(rows), "前三": rows[:3], "后三": rows[-3:]}
            return f, [rows[0].split("：")[0], rows[0].split("：")[1]], ["ads_trade_province_sales_monthly"]
        topics.append(("province", "省份销售月报", prov))

        def channel(m):
            rows = q("SELECT pay_channel, pay_cnt, pay_amt FROM ads_pay_channel_monthly WHERE stat_month=%s "
                     "ORDER BY pay_amt DESC", m)
            tot = sum(float(r[2]) for r in rows)
            f = {"支付金额合计": money(tot), "各渠道": [f"{r[0]}：{num(r[1])} 笔，{money(r[2])}，占比 {pct(float(r[2]) / tot)}"
                                                for r in rows]}
            return f, [f["支付金额合计"]], ["ads_pay_channel_monthly"]
        topics.append(("paychannel", "支付渠道月报", channel))

        def refund(m):
            cur = q("SELECT paid_order_cnt, refund_order_cnt, refund_rate FROM ads_trade_refund_rate_monthly "
                    "WHERE stat_month=%s", m)[0]
            p = prev(m)
            pr = q("SELECT refund_rate FROM ads_trade_refund_rate_monthly WHERE stat_month=%s", p)[0][0] if p else None
            reasons = q("SELECT refund_reason, SUM(refund_cnt), SUM(refund_amount) FROM dws_trade_reason_refund_1m "
                        "WHERE stat_date=%s GROUP BY refund_reason ORDER BY 2 DESC", first_day(m))
            f = {"退款率（按下单月）": pct(cur[2]), "上月退款率": pct(pr) if pr is not None else "无",
                 "支付订单数": num(cur[0]), "退款订单数": num(cur[1]),
                 "本月完成的退款按原因（按退款完成月）": [f"{r[0]}：{num(r[1])} 笔，{money(r[2])}" for r in reasons]}
            return f, [f["退款率（按下单月）"], f["退款订单数"]], ["ads_trade_refund_rate_monthly",
                                                         "dws_trade_reason_refund_1m"]
        topics.append(("refund", "退款月报", refund))

        def lgs(m):
            rows = q("SELECT warehouse_region, ship_cnt, avg_ship_hours, avg_deliver_hours FROM ads_lgs_delivery_monthly "
                     "WHERE stat_month=%s ORDER BY ship_cnt DESC", m)
            car = q("SELECT carrier, ship_cnt, signed_cnt, avg_ship_hours FROM dws_lgs_carrier_ship_1m WHERE stat_date=%s "
                    "ORDER BY ship_cnt DESC", first_day(m))
            f = {"各仓库大区": [f"{r[0]}：发货 {num(r[1])} 单，平均发货 {r[2]} 小时，平均配送 {r[3]} 小时" for r in rows],
                 "各承运商": [f"{r[0]}：发货 {num(r[1])} 单，签收率 {pct(r[2] / r[1])}" for r in car]}
            return f, [rows[0][0]], ["ads_lgs_delivery_monthly", "dws_lgs_carrier_ship_1m"]
        topics.append(("logistics", "物流时效月报", lgs))

        def review(m):
            rows = q("SELECT category1_name, review_cnt, avg_rating, good_rate FROM ads_item_review_score_monthly "
                     "WHERE stat_month=%s ORDER BY avg_rating DESC", m)
            f = {"评价总数": num(sum(r[1] for r in rows)),
                 "各一级品类（按平均评分降序）": [f"{r[0]}：{num(r[1])} 条，平均 {r[2]} 分，好评率 {pct(r[3])}" for r in rows]}
            return f, [f["评价总数"], rows[0][0]], ["ads_item_review_score_monthly"]
        topics.append(("review", "商品评价月报", review))

        def traffic(m):
            r = q("SELECT ROUND(AVG(uv)), ROUND(AVG(conversion_rate),4), SUM(buyer_cnt) FROM ads_traffic_conversion_daily "
                  "WHERE DATE_FORMAT(stat_date,'%%Y-%%m')=%s", m)[0]
            best = q("SELECT stat_date, conversion_rate FROM ads_traffic_conversion_daily WHERE "
                     "DATE_FORMAT(stat_date,'%%Y-%%m')=%s ORDER BY conversion_rate DESC LIMIT 1", m)[0]
            ch = q("SELECT channel, pv, uv FROM dws_traffic_vchannel_visit_1m WHERE stat_date=%s ORDER BY pv DESC",
                   first_day(m))
            f = {"日均访客数": num(r[0]), "日均转化率": pct(r[1]), "转化率最高的一天": f"{best[0]}，{pct(best[1])}",
                 "各访问渠道（月）": [f"{c[0]}：浏览量 {num(c[1])}，访客数 {num(c[2])}" for c in ch]}
            return f, [f["日均访客数"], f["日均转化率"]], ["ads_traffic_conversion_daily", "dws_traffic_vchannel_visit_1m"]
        topics.append(("traffic", "流量转化月报", traffic))

        def coupon(m):
            rows = q("SELECT coupon_type, receive_cnt, used_cnt, use_rate FROM ads_coupon_usage_monthly "
                     "WHERE stat_month=%s ORDER BY receive_cnt DESC", m)
            f = {"各券类型（按领取月）": [f"{r[0]}：领取 {num(r[1])}，使用 {num(r[2])}，核销率 {pct(r[3])}" for r in rows],
                 "领取合计": num(sum(r[1] for r in rows))}
            return f, [f["领取合计"]], ["ads_coupon_usage_monthly"]
        topics.append(("coupon", "优惠券使用月报", coupon))

        def level(m):
            rows = q("SELECT member_level, order_cnt, paid_amount FROM dws_trade_level_order_1m WHERE stat_date=%s "
                     "ORDER BY member_level", first_day(m))
            f = {"各会员等级（1-普通 … 5-钻石）": [f"等级 {r[0]}：下单 {num(r[1])} 单，已支付明细金额 {money(r[2])}"
                                          for r in rows]}
            return f, [money(rows[0][2])], ["dws_trade_level_order_1m"]
        topics.append(("member", "会员等级消费月报", level))

        def brand(m):
            rows = q("SELECT brand_name, order_cnt, paid_amount FROM dws_trade_brand_order_1m WHERE stat_date=%s "
                     "ORDER BY paid_amount DESC", first_day(m))
            f = {"品牌数": len(rows), "前五": [f"{r[0]}：{num(r[1])} 单，{money(r[2])}" for r in rows[:5]],
                 "末位": f"{rows[-1][0]}：{money(rows[-1][2])}"}
            return f, [rows[0][0]], ["dws_trade_brand_order_1m"]
        topics.append(("brand", "品牌销售月报", brand))

        for key, cn, fn in topics:
            for m in months:
                facts, required, tables = fn(m)
                facts = {"报告": cn, "月份": m, **facts}
                jobs.append(md_job(f"doc-monthly-{key}-{m}", "monthly", f"{cn}：{m}", ["概览", "分维度", "结论"],
                                   facts, required=[m] + required, min_chars=500, max_chars=2400,
                                   allow_md_table=True,
                                   meta={"docType": "主题月报", "tables": tables, "effectiveDate": f"{m}-28"}))
        return jobs

    # ---------- 周报 ----------

    def weekly_reports(self):
        q = self.f.query
        weeks = [r[0] for r in q("SELECT DISTINCT stat_date FROM dws_trade_prov_order_1w ORDER BY 1")][:52]
        jobs = []
        prev_amt = None
        for i, w in enumerate(weeks):
            o = q("SELECT SUM(order_cnt), SUM(paid_amount) FROM dws_trade_prov_order_1w WHERE stat_date=%s", w)[0]
            top = q("SELECT receiver_province, paid_amount FROM dws_trade_prov_order_1w WHERE stat_date=%s "
                    "ORDER BY paid_amount DESC LIMIT 1", w)[0]
            p = q("SELECT SUM(pay_cnt), SUM(pay_amt) FROM dws_trade_prov_pay_1w WHERE stat_date=%s", w)[0]
            r = q("SELECT SUM(refund_cnt), SUM(refund_amount) FROM dws_trade_prov_refund_1w WHERE stat_date=%s", w)[0]
            s = q("SELECT SUM(ship_cnt), SUM(signed_cnt) FROM dws_lgs_prov_ship_1w WHERE stat_date=%s", w)[0]
            v = q("SELECT SUM(review_cnt), ROUND(SUM(avg_rating*review_cnt)/SUM(review_cnt),2) FROM "
                  "dws_item_cate1_review_1w WHERE stat_date=%s", w)[0]
            t = q("SELECT COUNT(*), SUM(pv) FROM dws_traffic_visitor_visit_1w WHERE stat_date=%s", w)[0]
            end = w + timedelta(days=6)
            facts = {"周": f"{w} ～ {end}（周一到周日）", "下单数": num(o[0] or 0), "已支付明细金额": money(o[1] or 0),
                     "已支付明细金额周环比": change(o[1], prev_amt), "已支付明细金额最高的省份": f"{top[0]}，{money(top[1])}",
                     "支付": f"{num(p[0] or 0)} 笔，{money(p[1] or 0)}", "退款（按退款完成日）": f"{num(r[0] or 0)} 笔，{money(r[1] or 0)}",
                     "发货": f"{num(s[0] or 0)} 单，其中已签收 {num(s[1] or 0)} 单",
                     "评价": f"{num(v[0] or 0)} 条，平均评分（按评价数加权）{v[1]}", "访客数（周去重）": num(t[0]), "浏览量": num(t[1] or 0)}
            prev_amt = o[1]
            jobs.append(md_job(f"doc-weekly-{w}", "weekly", f"经营周报：{w} 当周",
                               ["概览", "交易与支付", "退款与物流", "流量与评价"], facts,
                               required=[str(w), facts["已支付明细金额"], facts["访客数（周去重）"]],
                               min_chars=600, max_chars=2400, allow_md_table=True,
                               meta={"docType": "经营周报", "effectiveDate": str(end),
                                     "tables": ["dws_trade_prov_order_1w", "dws_trade_prov_pay_1w",
                                                "dws_trade_prov_refund_1w", "dws_lgs_prov_ship_1w",
                                                "dws_item_cate1_review_1w", "dws_traffic_visitor_visit_1w"]}))
        return jobs

    # ---------- 变更公告（先生成事件，供指标卡引用） ----------

    def change_events(self, total=150):
        r = rng_for("changes")
        q = self.f.query
        events = []
        # 1. 补充字段注释：退化后的库里注释为空、干净元数据里有注释的有效表字段
        rows = q("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema=%s AND "
                 "column_comment=''", self.f.schema)
        missing = [(t, c) for t, c in rows if t in self.f.effective and c in self.f.tables[t]["columns"]
                   and self.f.tables[t]["columns"][c]["comment"]]
        for t, c in r.sample(sorted(missing), min(25, len(missing))):
            events.append({"类型": "补充字段注释", "表": t, "字段": c,
                           "注释": self.f.tables[t]["columns"][c]["comment"]})
        # 2. 噪声表下线
        cands = sorted(self.f.orphan_noise) + sorted(n for n in self.f.noise if n.startswith("test_"))
        for n in r.sample(cands, 30):
            events.append({"类型": "计划下线临时表 / 个人副本 / 测试表", "表": n})
        # 3. 备份表列名与正表不一致
        renames = [x for x in __import__("json").loads(
            (Path(__file__).resolve().parent.parent / "out" / "s2-large" / "degrade_log.json").read_text(
                encoding="utf-8"))["renames"]]
        for x in r.sample(sorted(renames), 15):
            tbl, rest = x.split(".", 1)
            old, new = rest.split("->")
            events.append({"类型": "提醒：备份 / 副本表列名与正表不一致", "表": tbl, "正表列名": old, "副本列名": new})
        # 4. 调度时间调整、5. 新表上线
        derived = [t for t in self.f.effective if layer_of(t) in ("DWS", "ADS", "DWD", "DIM")]
        for t in r.sample(derived, 20):
            a, b = r.choice([("03:30", "04:00"), ("05:00", "05:30"), ("02:30", "02:00"), ("03:30", "03:00")])
            events.append({"类型": "调度时间调整", "表": t, "原开始时间": a, "新开始时间": b})
        for t in r.sample(derived, 30):
            events.append({"类型": "新表上线", "表": t, "表注释": self.f.comment(t),
                           "上游": self.f.upstream.get(t, [])})
        # 6. 指标计算规则说明（澄清，不改数）
        clar = [("refund_rate", "退款率按下单月归属，分母为该月下单且已支付的订单数"),
                ("gmv", "GMV 统计已支付过的订单（pay_time 不为空），含后续退款"),
                ("use_rate", "核销率按优惠券领取月归属"), ("uv", "uv 为去重访客数，不能跨天相加"),
                ("avg_rating", "月平均评分按评价数加权"), ("conversion_rate", "转化率分母为当日商品详情页访客数"),
                ("repurchase_rate", "复购率按自然年统计，支付订单数≥2 视为复购"),
                ("avg_deliver_hours", "平均配送时长只统计已签收的物流单"),
                ("paid_amount", "paid_amount 为已支付订单的明细金额，不扣优惠和退款"),
                ("sales_amount", "销售额与 GMV 计算规则相同，按一级品类或省份拆分"),
                ("signed_cnt", "签收以物流单 sign_time 不为空为准"),
                ("pay_amt", "支付金额按支付日期归属，与按下单日期归属的 GMV 不同"),
                ("refund_amount", "退款金额按退款完成日期归属"),
                ("avg_ship_hours", "发货时长从支付时间算到发货时间"),
                ("item_qty", "购买件数含未支付订单，统计已支付件数请用 ADS 品类报表")]
        for col, text in clar:
            events.append({"类型": "指标计算规则说明", "指标": col, "说明": text})
        # 7. 表注释修正
        for t in r.sample([t for t in self.f.effective if layer_of(t) == "CORE"], 15):
            events.append({"类型": "表注释修正", "表": t, "正确注释": self.f.comment(t)})
        r.shuffle(events)
        events = events[:total]
        days = sorted(r.sample(range(0, 364), len(events)))
        for e, d in zip(events, days):
            e["生效日期"] = str(date(YEAR, 1, 1) + timedelta(days=d))
            e["发布团队"] = r.choice(TEAMS)
        return events

    def change_notices(self, events):
        jobs = []
        for i, e in enumerate(events, 1):
            target = e.get("表") or e.get("指标")
            req = [e["生效日期"], target]
            jobs.append(md_job(f"doc-change-{i:03d}", "change", f"变更公告：{e['类型']}（{target}）",
                               ["变更说明"], e, required=req, min_chars=200, max_chars=900,
                               meta={"docType": "变更公告", "effectiveDate": e["生效日期"],
                                     "tables": [e["表"]] if "表" in e else [],
                                     "metrics": [e["指标"]] if "指标" in e else []}))
        return jobs

    # ---------- 事故报告 ----------

    def incidents(self, total=50):
        r = rng_for("incidents")
        kinds = [("产出延迟", ["上游 ODS 同步超时", "集群资源不足排队", "依赖任务失败后重跑"]),
                 ("数据重复", ["任务重跑前未清理当天分区", "上游同步重复投递"]),
                 ("分区缺失", ["调度依赖配置错误，任务被跳过", "上游当天无数据未告警"]),
                 ("关键字段为空", ["上游业务系统字段改名", "关联维表时维表未更新"]),
                 ("计算结果偏差", ["SQL 修改时漏掉过滤条件", "维表 SCD 处理错误"])]
        derived = [t for t in self.f.effective if layer_of(t) in ("DWD", "DWS", "ADS", "DIM")]
        days = sorted(r.sample(range(0, 364), total))
        jobs = []
        for i, d in enumerate(days, 1):
            t = r.choice(derived)
            kind, causes = r.choice(kinds)
            down = sorted(self.f.downstream.get(t, []))
            down2 = sorted({x for y in down for x in self.f.downstream.get(y, [])})
            day = date(YEAR, 1, 1) + timedelta(days=d)
            facts = {"日期": str(day), "事故类型": kind, "直接受影响的表": t, "表注释": self.f.comment(t),
                     "原因": r.choice(causes), "持续时长": f"{r.choice([1, 2, 3, 4, 6, 8, 12])} 小时",
                     "受影响的下游": (down + down2)[:10], "处理": r.choice(["重跑当天任务并补数", "修复 SQL 后回刷近 7 天",
                                                                    "清理重复分区后重跑", "补齐维表后重跑"]),
                     "是否已补数": r.choice(["是", "是", "是", "部分补数"]), "负责团队": r.choice(TEAMS)}
            jobs.append(md_job(f"doc-incident-{i:03d}", "incident", f"数据事故报告：{day} {t} {kind}",
                               ["现象", "原因与影响范围"], facts, required=[str(day), t], min_chars=300, max_chars=1400,
                               meta={"docType": "数据事故报告", "effectiveDate": str(day), "tables": [t]}))
        return jobs

    # ---------- 取数需求单 ----------

    def demand_tickets(self, total=300):
        r = rng_for("tickets")
        fams = self.dws_families()
        enums = {c: [v for v, _ in vals] for _, c, _, vals in self.f.enums()}
        provinces = [x[0] for x in self.f.query("SELECT DISTINCT receiver_province FROM trade_order ORDER BY 1")]
        impossible = TOPICS["faq"]["capability_boundaries"]
        seen, jobs = set(), []
        days = sorted(r.choices(range(0, 364), k=total))
        i = 0
        while len(jobs) < total:
            i += 1
            day = date(YEAR, 1, 1) + timedelta(days=days[len(jobs)])
            if r.random() < 0.08:
                need = r.choice(impossible)
                key = ("x", need, r.randrange(5))
                facts = {"需求类型": "超出数据范围", "需求": need, "结论": "无法满足，说明原因并给出可替代的分析"}
                tables = []
            else:
                fam = r.choice(list(fams))
                period = r.choice(["1d", "1w", "1m"])
                table = f"{fam}_{period}"
                if table not in self.f.tables:
                    continue
                cols = self.f.columns(table)
                measures = [c for c, _, _ in cols if c in self.metrics]
                metric = r.choice(measures)
                # 时间范围不晚于提出日期所在月
                last = day.month
                span = min(last, r.choice([1, 1, 2, 3]))
                end_m = r.randint(span, last)
                months = list(range(end_m - span + 1, end_m + 1))
                rng = f"{YEAR}-{months[0]:02d}" + (f" 至 {YEAR}-{months[-1]:02d}" if len(months) > 1 else "")
                filt = None
                if "receiver_province" in dict((c, 1) for c, _, _ in cols) and r.random() < 0.4:
                    filt = f"只看{r.choice(provinces)}"
                key = (table, metric, rng, filt)
                facts = {"需求类型": "取数", "指标": f"{metric}（{self.metrics[metric]['中文名']}）",
                         "维度与周期": f"{self.f.comment(table)}", "时间范围": rng, "筛选": filt or "无",
                         "推荐用表": table, "表字段": [c for c, _, _ in cols],
                         "各指标字段能否跨行相加": {m: bool(self.metrics[m].get("可加和")) for m in measures},
                         "结论": r.choice(["已交付一次性取数结果", "已交付，并沉淀为固定报表", "已交付，附 SQL 供需求方自助复用"])}
                tables = [table]
            if key in seen:
                continue
            seen.add(key)
            n = len(jobs) + 1
            facts.update({"编号": f"REQ-{YEAR}-{n:04d}", "提出日期": str(day), "提出部门": r.choice(DEPTS),
                          "处理人": r.choice(TEAMS)})
            jobs.append(md_job(f"doc-ticket-{n:04d}", "ticket", f"取数需求单 {facts['编号']}",
                               ["需求", "结论"], facts, required=[facts["编号"]] + tables, min_chars=200,
                               max_chars=1200, meta={"docType": "取数需求单", "effectiveDate": str(day),
                                                     "tables": tables}))
        return jobs

    # ---------- QA / FAQ / 业务知识 ----------

    def qa_jobs(self):
        jobs = []
        fields = ["title", "question", "answer", "sql"]
        for fam, tables in self.dws_families().items():
            facts = {"主题": f"{fam} 家族（日 / 周 / 月三张表）的典型用法", "表": [self.table_brief(t) for t in tables[:1]],
                     "同家族": tables, "不可加和的指标": [c for c, _, _ in self.f.columns(tables[0])
                                                if c in self.metrics and not self.metrics[c].get("可加和")]}
            jobs.append(json_job(f"qa-dws-{fam}", "qa", fields, facts, required=[tables[0][:-3]],
                                 meta={"category": "DWS 家族用法", "tables": tables}))
        for key, (_, event, source, date_col, cn, measures) in layer_rules.EVENTS.items():
            fam = [f for f in self.dws_families() if f.endswith(f"_{event}")]
            facts = {"主题": f"按任意时间范围统计{cn}类指标时，如何在日 / 周 / 月表之间选择", "相关家族": fam,
                     "周期定义": {k: v[1] for k, v in layer_rules.PERIODS.items()},
                     "指标": [{"指标": c, "中文名": cm, "可加和": a} for c, _, _, cm, a in measures],
                     "示例表": self.table_brief(fam[0] + "_1d")}
            jobs.append(json_job(f"qa-period-{key}", "qa", fields, facts, meta={"category": "周期表选择",
                                                                               "tables": [fam[0] + "_1d"]}))
        for t in [t for t in self.f.effective if t.startswith("ads_")]:
            facts = {"主题": f"{t}（{self.f.comment(t)}）的用法", "表": self.table_brief(t), "加工 SQL": self.f.sql.get(t)}
            jobs.append(json_job(f"qa-ads-{t}", "qa", fields, facts, required=[t],
                                 meta={"category": "ADS 用法", "tables": [t]}))
        for cat, items in (("明细计算", TOPICS["qa"]["detail_metrics"]), ("关联路径", TOPICS["qa"]["join_paths"]),
                           ("易错点", TOPICS["qa"]["pitfalls"])):
            for i, it in enumerate(items, 1):
                facts = {"主题": it["topic"], "表": [self.table_brief(t) for t in it["tables"]]}
                fks = [f"{k['table']}.{k['column']} → {k['ref_table']}.{k['ref_column']}" for k in self.f.foreign_keys
                       if k["table"] in it["tables"] or k["ref_table"] in it["tables"]]
                if fks:
                    facts["外键"] = fks
                jobs.append(json_job(f"qa-{cat}-{i:02d}", "qa", fields, facts, meta={"category": cat,
                                                                                    "tables": it["tables"]}))
        return jobs

    def faq_jobs(self):
        jobs = []
        fields = ["title", "question", "answer"]
        cats = TOPICS["faq"]
        metric_facts = {k: {kk: vv for kk, vv in v.items() if not kk.startswith("计算（")} for k, v in self.metrics.items()}
        for cat, key in (("易混指标", "confusing_metrics"), ("汇总规则", "aggregation_rules"), ("时间含义", "time_semantics")):
            for i, topic in enumerate(cats[key], 1):
                related = {k: v for k, v in metric_facts.items() if re.search(rf"(?<![a-z_]){re.escape(k)}(?![a-z_])", topic)}
                brief = {k: f"{v['中文名']}，{v['事件']}，日期归属 {v.get('日期归属', '-')}，可加和={v['可加和']}"
                         for k, v in metric_facts.items()}
                facts = {"主题": topic, "相关指标详情": related, "全部指标简表": brief,
                         "周期定义": {k: v[1] for k, v in layer_rules.PERIODS.items()}}
                jobs.append(json_job(f"faq-{cat}-{i:02d}", "faq", fields, facts, meta={"category": cat}))
        bounds = {"核心数据时间范围": str(self.f.query("SELECT MIN(order_time), MAX(order_time) FROM trade_order")[0]),
                  "访问日志范围": str(self.f.query("SELECT MIN(visit_time), MAX(visit_time) FROM log_visit")[0]),
                  "收货省份": [x[0] for x in self.f.query("SELECT DISTINCT receiver_province FROM trade_order")],
                  "调度": "ODS 01:00、DIM 02:00、DWD 02:30、DWS 03:30、ADS 05:00，处理 T-1 数据",
                  "噪声表": self._noise_type_summary(), "退化情况": "部分表和字段注释缺失；部分备份表列名缩写（如 category1_name → category1_nm）"}
        for cat, key in (("数据边界", "data_boundaries"), ("能力边界", "capability_boundaries")):
            for i, topic in enumerate(cats[key], 1):
                facts = {"主题": topic, "事实": bounds, "现有表": self.f.effective}
                jobs.append(json_job(f"faq-{cat}-{i:02d}", "faq", fields, facts, meta={"category": cat}))
        for kind, v in self._noise_type_summary().items():
            facts = {"主题": f"{kind}能不能用来统计", "类型": kind, **v,
                     "原则": "统计一律用正表；分表是残缺副本（每月只取部分数据），备份和副本可能缺列或列名不同"}
            jobs.append(json_job("faq-噪声表-" + re.sub(r'[\s*<>:"/\\|?]', "", kind), "faq", fields, facts, required=v["示例"][:1],
                                 meta={"category": "噪声表规则"}))
        for t, c, cm, vals in self.f.enums()[:12]:
            facts = {"主题": f"{t}.{c} 有哪些取值、分别是什么意思", "字段注释": cm, "取值与行数": vals}
            jobs.append(json_job(f"faq-枚举-{t}-{c}", "faq", fields, facts, required=[c],
                                 meta={"category": "枚举值", "tables": [t]}))
        return jobs

    def business_jobs(self):
        jobs = []
        fields = ["businessTerm", "description", "synonyms"]
        for key, m in self.metrics.items():
            facts = {"类型": "指标", **{k: v for k, v in m.items() if not k.startswith("计算（")}}
            jobs.append(json_job(f"biz-metric-{key}", "business", fields, facts, meta={"metric": key}))
        for i, t in enumerate(TOPICS["business_terms"], 1):
            jobs.append(json_job(f"biz-term-{i:02d}", "business", fields, {"类型": "业务术语", **t},
                                 meta={"term": t["term"]}))
        return jobs

    def all_jobs(self):
        events = self.change_events()
        docs = (self.table_pages() + self.metric_cards(events) + self.etl_docs() + self.manuals()
                + self.monthly_reports() + self.weekly_reports() + self.change_notices(events) + self.incidents()
                + self.demand_tickets())
        return docs, self.qa_jobs() + self.faq_jobs(), self.business_jobs()
