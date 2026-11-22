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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.service.schemalink.LegacySchemaLinker;
import com.alibaba.cloud.ai.dataagent.service.schemalink.SchemaLinkingMode;
import org.junit.jupiter.api.Test;

/**
 * Task 7 新增的评测器功能：实现选择、默认评测名、可复现性比较。
 */
class BenchmarkSelectionTest {

	@Test
	void registry_containsLegacyUnderSameNameAsProductionMode() {
		// 评测器里的名字必须和生产配置 schema-linking.mode 的取值一致
		assertTrue(SchemaLinkerRegistry.names().contains(SchemaLinkingMode.LEGACY));
		assertEquals(LegacySchemaLinker.NAME, SchemaLinkingMode.LEGACY);
	}

	@Test
	void registry_unknownName_listsAvailableNames() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SchemaLinkerRegistry.create("no-such-linker", null));

		assertEquals("Unknown linker 'no-such-linker', available: [legacy, agentic, agentic-warm]", ex.getMessage());
	}

	@Test
	void config_labelDefaultsToLinkerName_andDeterminismCheckOnByDefault() {
		BenchmarkConfig config = BenchmarkConfig.from(Collections.emptyMap(), Collections.emptyMap());

		assertEquals("legacy", config.getLinker());
		assertEquals("legacy", config.getLabel());
		assertTrue(config.isVerifyDeterminism());
	}

	@Test
	void config_overrides() {
		Map<String, String> props = new HashMap<>();
		props.put("benchmark.linker", "multi-route");
		props.put("benchmark.verify-determinism", "false");

		BenchmarkConfig config = BenchmarkConfig.from(props, Collections.emptyMap());

		assertEquals("multi-route", config.getLabel());
		assertFalse(config.isVerifyDeterminism());
	}

	@Test
	void config_schemaOverride_buildsJdbcUrlForThatSchema() {
		BenchmarkConfig config = BenchmarkConfig.from(Collections.singletonMap("benchmark.schema", "enterprise_dw_l"),
				Collections.emptyMap());

		assertEquals("enterprise_dw_l", config.getSchemaName());
		assertEquals("jdbc:mysql://127.0.0.1:3306/enterprise_dw_l", config.displayJdbcUrl());
	}

	@Test
	void compareRankings_identical_returnsEmpty_elapsedIgnored() {
		List<QuestionResult> first = Collections.singletonList(result("q1", Arrays.asList("a", "b"), 10));
		List<QuestionResult> second = Collections.singletonList(result("q1", Arrays.asList("a", "b"), 99));

		assertTrue(QuestionResult.compareRankings(first, second).isEmpty());
	}

	@Test
	void compareRankings_orderDiffers_isReported() {
		List<QuestionResult> first = Collections.singletonList(result("q1", Arrays.asList("a", "b"), 10));
		List<QuestionResult> second = Collections.singletonList(result("q1", Arrays.asList("b", "a"), 10));

		assertEquals(Collections.singletonList("q1: [a, b] vs [b, a]"), QuestionResult.compareRankings(first, second));
	}

	@Test
	void compareRankings_differentQuestionOrder_isRejected() {
		List<QuestionResult> first = Collections.singletonList(result("q1", Collections.singletonList("a"), 1));
		List<QuestionResult> second = Collections.singletonList(result("q2", Collections.singletonList("a"), 1));

		assertThrows(IllegalArgumentException.class, () -> QuestionResult.compareRankings(first, second));
	}

	@Test
	void describeDeterminism_threeStates() {
		assertEquals("未检查", BenchmarkReportWriter.describeDeterminism(null));
		assertEquals("两次独立运行逐题结果一致", BenchmarkReportWriter.describeDeterminism(true));
		assertTrue(BenchmarkReportWriter.describeDeterminism(false).contains("不可信"));
	}

	private static QuestionResult result(String id, List<String> ranked, long millis) {
		return new QuestionResult(id, "L1", Collections.emptyList(), ranked,
				Collections.singletonList(Collections.singleton("a")), millis);
	}

}
