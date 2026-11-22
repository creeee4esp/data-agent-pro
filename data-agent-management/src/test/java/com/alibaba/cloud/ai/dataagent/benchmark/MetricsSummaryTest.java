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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MetricsSummaryTest {

	private static final double EPS = 1e-9;

	private static final List<Integer> KS = Arrays.asList(1, 2);

	@Test
	void summary_overallAveragesOverQuestions() {
		// q1：两张表都在前 2 → K=1 召回 0.5、K=2 全命中
		// q2：一张表也没召回到 → 全是 0
		MetricsSummary summary = MetricsSummary.of(Arrays.asList(
				result("q1", "L2", Arrays.asList("multi_hop"), Arrays.asList("a", "b"), setOf("a", "b"), 10),
				result("q2", "L1", Arrays.asList("value_match"), Arrays.asList("x"), setOf("c"), 30)), KS);

		MetricsSummary.GroupMetrics overall = summary.getOverall();
		assertEquals(2, overall.getQuestionCount());
		assertEquals((0.5 + 0) / 2, overall.getTableRecall().get(1), EPS);
		assertEquals((1.0 + 0) / 2, overall.getTableRecall().get(2), EPS);
		assertEquals(0.0, overall.getFullHitRate().get(1), EPS);
		assertEquals(0.5, overall.getFullHitRate().get(2), EPS);
		assertEquals(10L, overall.getP50Millis());
		assertEquals(30L, overall.getP95Millis());
	}

	@Test
	void summary_groupsByTagAndDifficulty_questionWithTwoTagsCountsInBoth() {
		MetricsSummary summary = MetricsSummary.of(Arrays.asList(
				result("q1", "L2", Arrays.asList("multi_hop", "metric"), Arrays.asList("a"), setOf("a"), 5),
				result("q2", "L2", Arrays.asList("metric"), Arrays.asList("x"), setOf("b"), 5),
				result("q3", "L1", Arrays.asList("value_match"), Arrays.asList("c"), setOf("c"), 5)), KS);

		// 标签分组：metric 有 q1、q2；multi_hop 只有 q1；按字母序排列
		assertEquals(Arrays.asList("metric", "multi_hop", "value_match"),
				Arrays.asList(summary.getByTag().keySet().toArray()));
		assertEquals(2, summary.getByTag().get("metric").getQuestionCount());
		assertEquals(0.5, summary.getByTag().get("metric").getFullHitRate().get(1), EPS);
		assertEquals(1.0, summary.getByTag().get("multi_hop").getFullHitRate().get(1), EPS);

		// 难度分组
		assertEquals(2, summary.getByDifficulty().get("L2").getQuestionCount());
		assertEquals(1, summary.getByDifficulty().get("L1").getQuestionCount());
	}

	@Test
	void summary_emptyInput_isRejected() {
		assertThrows(IllegalArgumentException.class, () -> MetricsSummary.of(Collections.emptyList(), KS));
	}

	private static QuestionResult result(String id, String difficulty, List<String> tags, List<String> ranked,
			Set<String> gold, long millis) {
		return new QuestionResult(id, difficulty, tags, ranked, Collections.singletonList(gold), millis);
	}

	private static Set<String> setOf(String... values) {
		return new HashSet<>(Arrays.asList(values));
	}

}
