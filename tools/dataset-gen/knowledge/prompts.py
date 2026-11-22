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

"""写作提示词：把 job 变成发给大模型的消息。正文完全由大模型撰写，这里只给要求和事实。"""
import json

SYSTEM = """你是一家电商公司数据团队的资深数据工程师，正在为公司内部的数据知识库撰写内容。读者是数据分析师、业务同事和一个会自动写 SQL 的数据智能体。检索时读者往往只看到其中一个章节，所以每个章节都要能单独读懂。

硬性要求：
1. 只能使用用户给出的事实。不得编造事实里没有的表名、字段名、数字、日期、人名或原因。数字和日期必须原样照抄（包括千分位、小数位和单位）。
2. 表名、字段名用反引号包裹，例如 `trade_order`、`pay_time`。
3. 不要写"本文档由……生成""如有疑问请联系"之类的套话。"""

KIND_CN = {"table": "表说明页", "metric": "指标说明卡", "etl": "加工说明", "manual": "规范手册", "monthly": "主题月报",
           "weekly": "经营周报", "change": "变更公告", "incident": "数据事故报告", "ticket": "取数需求单记录"}

GUIDE = {
    "table": "字段一节用无序列表逐个说明事实中的全部字段，每行一句完整的话：字段名、含义、使用时要注意的地方；不要用 Markdown 表格。"
             "上下游一节说明数据从哪里来、被哪些表使用。使用注意一节说明适合和不适合用它做哪些查询；如果事实里有同家族的分表、备份表、测试表，"
             "说明它们是什么、为什么不能用于统计（可以举一两个表名，不必全部列出）。",
    "metric": "计算公式一节写清聚合方式或分子分母、时间归属、能否跨行或跨天相加；取哪张表一节按日 / 周 / 月和不同维度说明该用哪张表，"
              "只能提事实里出现的表；来源与历史变化一节说明来自哪张明细表，以及事实中的历史变更（有的话写明生效日期）。",
    "etl": "加工逻辑一节用文字解释加工 SQL 做了什么，可以引用关键片段（用 ```sql 代码块）；依赖与调度一节写依赖的上游表和调度时间；"
           "质量检查一节写检查项，以及检查失败时下游会受什么影响。",
    "manual": "每个二级标题是一章，写成可以执行的规范条款（可以编号），每章 400～1000 字；尽量用事实中的真实表名做正例或反例。",
    "monthly": "这是数据团队每月发给业务方的报告：概览给出最重要的几个数字和变化；分维度展开（可以用 Markdown 表格）；"
               "结论给出 2～4 条判断或建议，只能基于给出的数字，不要编造业务原因。",
    "weekly": "这是数据团队每周发给管理层的周报：概览一段话总结本周；后面三节分别展开对应的数字（可以用 Markdown 表格）。"
              "只能基于给出的数字，不要编造原因。",
    "change": "这是一则简短的变更公告：写明变更内容、生效日期、影响范围，以及使用方需要做什么。",
    "incident": "现象一节写发现时间和表现；原因与影响范围一节写原因、受影响的下游表、处理方式和补数情况。语气客观。",
    "ticket": "这是需求单系统里的一条记录。需求一节用业务方的口吻概括需求（哪个部门、要什么、时间范围、筛选条件）；"
              "结论一节写数据团队怎么处理：用了哪张表、哪些字段、要注意什么（例如指标能否相加），以及交付结果。"
              "超出数据范围的需求，说明原因并给出可替代的分析。不要写完整 SQL。",
}

JSON_GUIDE = {
    "qa": "写一条\"分析范例\"类问答（QA），供数据智能体在写 SQL 前参考。输出 JSON 对象，字段：\n"
          "- title：10～25 字的标题；\n"
          "- question：业务方口吻提出的一个具体分析需求，20～60 字；\n"
          "- answer：思考步骤和取数逻辑，200～600 字，说明选哪张表、为什么、怎么关联、要注意什么，最后附上完整 SQL；\n"
          "- sql：与 answer 末尾相同的那条 MySQL 8 SELECT 语句，只能用事实中的表和字段，能直接执行且结果不为空，"
          "时间条件限定在 2025 年内。",
    "faq": "写一条 FAQ，回答使用数据时的常见疑问。输出 JSON 对象，字段：\n"
           "- title：10～25 字的标题；\n"
           "- question：使用者会怎么问，15～50 字；\n"
           "- answer：直接、准确的回答，80～400 字，只依据事实；涉及的表名、字段名要写出来。",
    "business": "写一条业务知识（业务名词词条），供数据智能体理解业务方的说法。输出 JSON 对象，字段：\n"
                "- businessTerm：中文业务名词，2～10 字，不要把英文字段名当作名词；\n"
                "- description：含义或计算规则，40～200 字，写清分子分母、时间归属和对应的字段或表；\n"
                "- synonyms：2～5 个业务方常用的同义说法（字符串数组），不能包含 businessTerm 本身。",
}


def build_messages(job):
    facts = json.dumps(job["facts"], ensure_ascii=False, indent=1, default=str)
    if job["output"] == "md":
        sections = "、".join(f"## {s}" for s in job["sections"])
        table_rule = "" if job["allow_md_table"] else "不要使用 Markdown 表格。"
        user = (f"请写一篇{KIND_CN[job['kind']]}。\n"
                f"第一行原样输出：# {job['title']}\n"
                f"之后只使用这些二级标题，顺序不变，不增不减：{sections}。二级标题下可以用 ### 三级标题。\n"
                f"篇幅：{job['min_chars']}～{job['max_chars']} 个字符。\n"
                f"写作风格：{job['style']}。\n"
                f"{GUIDE[job['kind']]}{table_rule}\n"
                "每个二级标题下的第一句话写明主体（表名、指标名或报告期），不要用\"本表\"\"该指标\"开头。\n"
                "只输出 Markdown 正文，不要用代码围栏包裹整篇，不要 YAML 头部。\n\n"
                f"事实（JSON）：\n{facts}")
    else:
        user = f"{JSON_GUIDE[job['kind']]}\n写作风格：{job['style']}。\n只输出 JSON 对象，不要其他内容。\n\n事实（JSON）：\n{facts}"
    return [{"role": "system", "content": SYSTEM}, {"role": "user", "content": user}]


def feedback_message(problems):
    return {"role": "user", "content": "上一版有以下问题，请修正后重新输出完整内容（格式要求不变）：\n- "
                                       + "\n- ".join(problems)}
