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

"""纯函数单元测试（不需要数据库）：python -m unittest discover -s tests"""
import random
import unittest

from gen import db, degrade, dist, validate


class SplitStatementsTest(unittest.TestCase):
    def test_skips_comments_and_splits_on_trailing_semicolon(self):
        sql = "-- header\nCREATE TABLE a (\n  x INT\n);\n\nINSERT INTO a\nSELECT 1;\n"
        self.assertEqual(db.split_statements(sql), ["CREATE TABLE a (\n  x INT\n)", "INSERT INTO a\nSELECT 1"])


class TableGroupTest(unittest.TestCase):
    def test_groups(self):
        core = ["trade_order"]
        self.assertEqual(db.table_group("trade_order", core), "core")
        self.assertEqual(db.table_group("dws_trade_user_order_1d", core), "derived")
        self.assertEqual(db.table_group("trade_order_bak", core), "noise")


class DeterminismTest(unittest.TestCase):
    def test_same_seed_same_samples(self):
        def draw(seed):
            rng = random.Random(seed)
            pick = dist.weighted_sampler(rng, list(range(100)), dist.zipf_weights(100))
            return [pick() for _ in range(50)], dist.person_name(rng), dist.money_cents(rng, 10, 100)

        self.assertEqual(draw(7), draw(7))
        self.assertNotEqual(draw(7), draw(8))

    def test_zipf_head_is_heavier(self):
        rng = random.Random(1)
        pick = dist.weighted_sampler(rng, list(range(100)), dist.zipf_weights(100))
        counts = [0] * 100
        for _ in range(20000):
            counts[pick()] += 1
        self.assertGreater(sum(counts[:5]), sum(counts[50:]))


class AbbreviateTest(unittest.TestCase):
    def test_abbreviates_known_token(self):
        self.assertEqual(degrade._abbreviate("pay_amount", random.Random(0), 1.0), "pay_amt")

    def test_no_known_token_or_zero_ratio(self):
        self.assertIsNone(degrade._abbreviate("buyer_id", random.Random(0), 1.0))
        self.assertIsNone(degrade._abbreviate("pay_amount", random.Random(0), 0.0))


class NormResultTest(unittest.TestCase):
    def test_numeric_and_order_normalization(self):
        import decimal
        a = [(1, decimal.Decimal("2.5")), (0, 3)]
        b = [(0, decimal.Decimal("3.00")), (1, 2.5)]
        self.assertEqual(validate._norm_result(a, False), validate._norm_result(b, False))
        self.assertNotEqual(validate._norm_result(a, True), validate._norm_result(b, True))


if __name__ == "__main__":
    unittest.main()
