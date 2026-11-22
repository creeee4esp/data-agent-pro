/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 9个用例 每个用例对应一类容易出错的 SQL 写法，其中"字符串里出现 FROM"是正则方案的典型误判，面试时可以拿来说明为什么用 AST。
 */

class SqlTableExtractorTest {

	@Test
	void extract_joinWithAliases_returnsPhysicalTablesNotAliases() {
		Set<String> tables = SqlTableExtractor.extractTables(
				"SELECT SUM(i.item_amount) FROM trade_order o JOIN trade_order_item i ON i.order_id = o.order_id");

		assertEquals(setOf("trade_order", "trade_order_item"), tables);
	}

	@Test
	void extract_subqueryAlias_isNotTreatedAsTable() {
		// 复购率题的写法：FROM (子查询) t，t 只是派生表的别名
		Set<String> tables = SqlTableExtractor.extractTables("SELECT ROUND(SUM(cnt >= 2) / COUNT(*) * 100, 2) FROM "
				+ "(SELECT buyer_id, COUNT(*) AS cnt FROM trade_order GROUP BY buyer_id) t");

		assertEquals(Collections.singleton("trade_order"), tables);
	}

	@Test
	void extract_cteName_isNotTreatedAsTable() {
		Set<String> tables = SqlTableExtractor.extractTables("WITH m AS (SELECT buyer_id FROM trade_order) "
				+ "SELECT u.user_name FROM m JOIN user_info u ON u.user_id = m.buyer_id");

		assertEquals(setOf("trade_order", "user_info"), tables);
	}

	@Test
	void extract_selfJoin_countsTableOnce() {
		// 一级、二级品类：同一张表连接两次
		Set<String> tables = SqlTableExtractor.extractTables(
				"SELECT c1.category_name FROM item_sku s " + "JOIN item_category c2 ON c2.category_id = s.category_id "
						+ "JOIN item_category c1 ON c1.category_id = c2.parent_id");

		assertEquals(setOf("item_sku", "item_category"), tables);
	}

	@Test
	void extract_schemaPrefixQuotesAndCase_areNormalized() {
		Set<String> tables = SqlTableExtractor
			.extractTables("SELECT COUNT(*) FROM `enterprise_dw`.`Trade_Order` WHERE order_status = 'cancelled'");

		assertEquals(Collections.singleton("trade_order"), tables);
	}

	@Test
	void extract_tableNameInsideStringLiteral_isIgnored() {
		// 正则方案的典型误判：字符串里出现 "FROM xxx"
		Set<String> tables = SqlTableExtractor.extractTables("SELECT 'FROM fake_table' AS s FROM user_info");

		assertEquals(Collections.singleton("user_info"), tables);
	}

	@Test
	void extract_multipleStatements_isRejected() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SqlTableExtractor.extractTables("SELECT 1 FROM a; SELECT 2 FROM b"));

		assertEquals("Expected exactly one statement but got 2", ex.getMessage());
	}

	@Test
	void extract_unparsableSql_isRejectedWithOriginalSql() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SqlTableExtractor.extractTables("SELEC * FORM trade_order"));

		assertTrue(ex.getMessage().contains("SELEC * FORM trade_order"));
	}

	@Test
	void extract_blankSql_isRejected() {
		assertThrows(IllegalArgumentException.class, () -> SqlTableExtractor.extractTables("  "));
	}

	private static Set<String> setOf(String... values) {
		return new HashSet<>(Arrays.asList(values));
	}

}
