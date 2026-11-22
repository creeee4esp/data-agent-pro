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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestionLoaderTest {

	/** 测试运行时的工作目录是 data-agent-management 模块目录，题集在仓库根目录的 tools 下。 */
	private static final Path M0_QUESTIONS = Paths.get("..", "tools", "dataset-gen", "questions", "m0.jsonl");

	@TempDir
	Path tempDir;

	@Test
	void load_m0_loadsAllQuestionsInFileOrder() throws IOException {
		List<BenchmarkQuestion> questions = QuestionLoader.load(M0_QUESTIONS);

		assertEquals(10, questions.size());
		assertEquals("m0-001", questions.get(0).getId());
		assertEquals("m0-010", questions.get(9).getId());
	}

	@Test
	void load_m0_gmvQuestionHasThreeAcceptableTableSets() throws IOException {
		BenchmarkQuestion gmv = findById(QuestionLoader.load(M0_QUESTIONS), "m0-003");

		// 顺序：标准 SQL 在前，替代 SQL 按题集中的顺序
		assertEquals(Arrays.asList(setOf("trade_order", "trade_order_item"),
				Collections.singleton("ads_trade_gmv_daily"), Collections.singleton("trade_order")),
				gmv.getAcceptableTableSets());
	}

	@Test
	void load_m0_questionWithoutAlternatives_hasOnlyGoldSet() throws IOException {
		BenchmarkQuestion q = findById(QuestionLoader.load(M0_QUESTIONS), "m0-006");

		assertEquals(
				Collections
					.singletonList(setOf("user_info", "trade_order", "trade_order_item", "item_sku", "item_category")),
				q.getAcceptableTableSets());
	}

	@Test
	void load_duplicateAlternativeTableSet_isDeduplicated() throws IOException {
		Path file = write("{\"id\":\"q1\",\"question\":\"取消订单数\",\"gold_sql\":\"SELECT COUNT(*) FROM trade_order\","
				+ "\"alt_sqls\":[\"SELECT COUNT(1) FROM trade_order\"],\"tables\":[\"trade_order\"]}");

		BenchmarkQuestion q = QuestionLoader.load(file).get(0);

		assertEquals(Collections.singletonList(Collections.singleton("trade_order")), q.getAcceptableTableSets());
	}

	@Test
	void load_blankLines_areSkipped() throws IOException {
		Path file = write("", "{\"id\":\"q1\",\"question\":\"q\",\"gold_sql\":\"SELECT 1 FROM user_info\","
				+ "\"tables\":[\"user_info\"]}", "   ");

		assertEquals(1, QuestionLoader.load(file).size());
	}

	@Test
	void load_malformedJson_failsWithLineNumber() throws IOException {
		Path file = write(
				"{\"id\":\"q1\",\"question\":\"q\",\"gold_sql\":\"SELECT 1 FROM user_info\",\"tables\":[\"user_info\"]}",
				"{\"id\":\"q2\", broken");

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> QuestionLoader.load(file));

		assertTrue(ex.getMessage().startsWith("questions.jsonl line 2:"), ex.getMessage());
	}

	@Test
	void load_unknownField_failsSoTyposAreCaught() throws IOException {
		// gold_sq 是 gold_sql 的拼写错误
		Path file = write("{\"id\":\"q1\",\"question\":\"q\",\"gold_sq\":\"SELECT 1 FROM user_info\","
				+ "\"tables\":[\"user_info\"]}");

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> QuestionLoader.load(file));

		assertTrue(ex.getMessage().contains("gold_sq"), ex.getMessage());
	}

	@Test
	void load_missingGoldSql_fails() throws IOException {
		Path file = write("{\"id\":\"q1\",\"question\":\"q\",\"tables\":[\"user_info\"]}");

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> QuestionLoader.load(file));

		assertEquals("questions.jsonl line 1: gold_sql must not be blank", ex.getMessage());
	}

	@Test
	void load_labeledTablesDisagreeWithGoldSql_fails() throws IOException {
		// 标注漏了 trade_order_item
		Path file = write("{\"id\":\"q1\",\"question\":\"q\",\"gold_sql\":\"SELECT 1 FROM trade_order o "
				+ "JOIN trade_order_item i ON i.order_id = o.order_id\",\"tables\":[\"trade_order\"]}");

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> QuestionLoader.load(file));

		assertTrue(ex.getMessage().contains("labeled tables [trade_order] != tables in gold_sql"), ex.getMessage());
	}

	private Path write(String... lines) throws IOException {
		Path file = tempDir.resolve("questions.jsonl");
		Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
		return file;
	}

	private static BenchmarkQuestion findById(List<BenchmarkQuestion> questions, String id) {
		for (BenchmarkQuestion q : questions) {
			if (id.equals(q.getId())) {
				return q;
			}
		}
		throw new AssertionError("question not found: " + id);
	}

	private static Set<String> setOf(String... values) {
		return new HashSet<>(Arrays.asList(values));
	}

}
