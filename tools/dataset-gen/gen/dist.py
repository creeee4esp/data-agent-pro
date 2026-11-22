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

"""随机分布与词表。全部基于传入的 random.Random，保证同一种子结果一致（ADR-004）。"""
import bisect
import datetime as dt
import itertools
import math

SURNAMES = list("王李张刘陈杨黄赵吴周徐孙马朱胡郭何高林罗郑梁谢宋唐许韩冯邓曹彭曾肖田董袁潘于蒋蔡余杜叶程苏魏吕丁任沈")
GIVEN_CHARS = list("伟芳娜敏静丽强磊军洋勇艳杰娟涛明超秀霞平刚桂英华玉兰萍鹏辉建红林斌宇浩凯琳晨欣怡子涵轩博文睿")

# (省份, 人口权重, 主要城市)
PROVINCES = [
    ("广东省", 12.6, ["广州市", "深圳市", "东莞市", "佛山市"]),
    ("山东省", 10.2, ["济南市", "青岛市", "烟台市"]),
    ("河南省", 9.9, ["郑州市", "洛阳市"]),
    ("江苏省", 8.5, ["南京市", "苏州市", "无锡市"]),
    ("四川省", 8.4, ["成都市", "绵阳市"]),
    ("河北省", 7.5, ["石家庄市", "唐山市"]),
    ("湖南省", 6.6, ["长沙市", "株洲市"]),
    ("浙江省", 6.5, ["杭州市", "宁波市", "温州市"]),
    ("安徽省", 6.1, ["合肥市", "芜湖市"]),
    ("湖北省", 5.8, ["武汉市", "宜昌市"]),
    ("福建省", 4.2, ["福州市", "厦门市"]),
    ("北京市", 2.2, ["北京市"]),
    ("上海市", 2.5, ["上海市"]),
    ("天津市", 1.4, ["天津市"]),
    ("重庆市", 3.2, ["重庆市"]),
    ("陕西省", 3.9, ["西安市", "宝鸡市"]),
]

# 一级品类 -> [(二级品类, 价格区间元, 商品名词)]
CATEGORIES = {
    "手机数码": [("手机", (999, 6999), ["智能手机", "5G手机", "折叠屏手机"]),
               ("耳机", (99, 1999), ["蓝牙耳机", "降噪耳机", "运动耳机"])],
    "电脑办公": [("笔记本", (2999, 12999), ["轻薄本", "游戏本", "商务本"]),
               ("显示器", (599, 3999), ["27英寸显示器", "4K显示器"])],
    "家用电器": [("厨房电器", (99, 2999), ["电饭煲", "空气炸锅", "破壁机"]),
               ("生活电器", (199, 3999), ["扫地机器人", "吸尘器", "加湿器"])],
    "服装鞋帽": [("男装", (59, 999), ["T恤", "夹克", "羽绒服"]),
               ("女装", (69, 1299), ["连衣裙", "风衣", "针织衫"]),
               ("运动鞋", (199, 1299), ["跑步鞋", "篮球鞋"])],
    "美妆个护": [("护肤", (49, 899), ["面霜", "精华液", "洁面乳"]),
               ("彩妆", (39, 499), ["口红", "粉底液"])],
    "食品饮料": [("零食", (9, 99), ["坚果礼盒", "牛肉干", "饼干"]),
               ("饮料", (5, 199), ["矿泉水", "咖啡", "茶饮料"])],
    "图书": [("小说", (19, 99), ["长篇小说", "悬疑小说"]),
           ("教辅", (15, 89), ["练习册", "考试真题"])],
}
BRANDS = ["星河", "云杉", "北辰", "青禾", "远山", "极光", "麦田", "海岸", "松果", "橙光", "石溪", "白鹭"]
PINYIN_NAMES = ["zhangsan", "lisi", "wangwu", "zhaoliu", "chenqi", "liuyang", "zhoujie", "sunmei"]


def weighted_sampler(rng, items, weights):
    """返回一个按权重抽样的函数（预计算累积权重，O(log n)）。"""
    cum = list(itertools.accumulate(weights))
    total = cum[-1]

    def sample():
        return items[bisect.bisect_right(cum, rng.random() * total)]

    return sample


def zipf_weights(n, s=1.1):
    return [1.0 / (k ** s) for k in range(1, n + 1)]


def person_name(rng):
    return rng.choice(SURNAMES) + "".join(rng.choice(GIVEN_CHARS) for _ in range(rng.choice((1, 2))))


def day_weights(year):
    """每日下单权重：线性增长趋势 + 周末上浮 + 大促峰值。"""
    start = dt.date(year, 1, 1)
    days = [start + dt.timedelta(d) for d in range((dt.date(year + 1, 1, 1) - start).days)]
    promos = {(6, 18): 4.0, (11, 11): 6.0, (12, 12): 2.5}
    warmups = [((6, 1), (6, 17), 1.4), ((11, 1), (11, 10), 1.6)]
    weights = []
    for i, d in enumerate(days):
        w = 1.0 + 0.3 * i / len(days)
        if d.weekday() >= 5:
            w *= 1.25
        w *= promos.get((d.month, d.day), 1.0)
        for (m1, d1), (m2, d2), f in warmups:
            if (m1, d1) <= (d.month, d.day) <= (m2, d2):
                w *= f
        weights.append(w)
    return days, weights


# 日内下单分布：午间、晚间双峰
HOUR_WEIGHTS = [0.4, 0.2, 0.1, 0.1, 0.1, 0.2, 0.5, 1.0, 1.6, 2.0, 2.4, 2.8,
                3.2, 2.6, 2.2, 2.2, 2.3, 2.5, 2.8, 3.4, 4.0, 4.2, 3.0, 1.4]


def money_cents(rng, low_yuan, high_yuan):
    """区间内对数均匀取价，尾数取 .00 或 .90，返回分。"""
    v = math.exp(rng.uniform(math.log(low_yuan), math.log(high_yuan)))
    yuan = int(v)
    return yuan * 100 + rng.choice((0, 0, 90))
