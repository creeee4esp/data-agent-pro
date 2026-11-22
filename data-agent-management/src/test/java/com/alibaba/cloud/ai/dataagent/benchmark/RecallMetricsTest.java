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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class RecallMetricsTest {

	/** 比较小数时允许的误差。 */
	private static final double EPS = 1e-9;

	@Test
	void tableRecallAtK_partialHit_returnsFractionOfGoldTables() {
		// 标准答案 3 张表，前 3 名命中其中 2 张
		List<Set<String>> acceptable = Collections.singletonList(setOf("a", "b", "c"));

		double recall = RecallMetrics.tableRecallAtK(Arrays.asList("a", "x", "b", "c"), acceptable, 3);

		assertEquals(2.0 / 3, recall, EPS);
	}

	@Test
	void tableRecallAtK_multipleAcceptableSets_takesBest() {
		// 第一组只命中一半，第二组（例如 ADS 报表表）完全命中 → 取 1.0
		List<Set<String>> acceptable = Arrays.asList(setOf("trade_order", "trade_order_item"),
				Collections.singleton("ads_trade_gmv_daily"));

		double recall = RecallMetrics.tableRecallAtK(Arrays.asList("ads_trade_gmv_daily", "trade_order"), acceptable,
				5);

		assertEquals(1.0, recall, EPS);
	}

	@Test
	void fullHitAtK_anyAcceptableSetCovered_counts() {
		List<Set<String>> acceptable = Arrays.asList(setOf("trade_order", "trade_order_item"),
				Collections.singleton("ads_trade_gmv_daily"));

		assertTrue(RecallMetrics.fullHitAtK(Arrays.asList("user_info", "ads_trade_gmv_daily"), acceptable, 2));
	}

	@Test
	void fullHitAtK_requiredTableBeyondK_notCounted() {
		// trade_order_item 排在第 3 名，K=2 时看不到
		List<Set<String>> acceptable = Collections.singletonList(setOf("trade_order", "trade_order_item"));
		List<String> ranked = Arrays.asList("trade_order", "user_info", "trade_order_item");

		assertFalse(RecallMetrics.fullHitAtK(ranked, acceptable, 2));
		assertTrue(RecallMetrics.fullHitAtK(ranked, acceptable, 3));
	}

	@Test
	void topK_duplicatesAndCase_doNotTakeExtraSlots() {
		// 重复出现的表只占一个名额；大小写不同视为同一张表
		Set<String> top = RecallMetrics.topK(Arrays.asList("Orders", "orders", "users", "items"), 2);

		assertEquals(setOf("orders", "users"), top);
	}

	@Test
	void metrics_emptyRecall_scoresZero() {
		List<Set<String>> acceptable = Collections.singletonList(setOf("a"));

		assertEquals(0.0, RecallMetrics.tableRecallAtK(Collections.emptyList(), acceptable, 10), EPS);
		assertFalse(RecallMetrics.fullHitAtK(Collections.emptyList(), acceptable, 10));
	}

	@Test
	void percentile_nearestRank() {
		List<Long> latencies = Arrays.asList(50L, 10L, 40L, 20L, 30L, 60L, 70L, 80L, 90L, 100L);

		// 排序后为 10..100：P50 → 第 5 个 = 50；P95 → 第 10 个 = 100；P10 → 第 1 个 = 10
		assertEquals(50L, RecallMetrics.percentile(latencies, 50));
		assertEquals(100L, RecallMetrics.percentile(latencies, 95));
		assertEquals(10L, RecallMetrics.percentile(latencies, 10));
	}

	@Test
	void percentile_singleValue_isThatValue() {
		assertEquals(7L, RecallMetrics.percentile(Collections.singletonList(7L), 95));
	}

	@Test
	void invalidArguments_areRejected() {
		List<Set<String>> acceptable = Collections.singletonList(setOf("a"));

		assertThrows(IllegalArgumentException.class,
				() -> RecallMetrics.tableRecallAtK(Collections.singletonList("a"), acceptable, 0));
		assertThrows(IllegalArgumentException.class,
				() -> RecallMetrics.fullHitAtK(Collections.singletonList("a"), Collections.emptyList(), 5));
		assertThrows(IllegalArgumentException.class, () -> RecallMetrics.percentile(Collections.emptyList(), 50));
	}

	private static Set<String> setOf(String... values) {
		return new HashSet<>(Arrays.asList(values));
	}

}
