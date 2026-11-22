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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lombok.Value;

/**
 * 一次评测的汇总指标：整体 + 按题型标签分组 + 按难度分组。
 * <p>
 * 按标签分组是消融分析的关键：每加一路召回，看它主要提升了哪类题（例如值索引应主要提升 value_match）。 一道题有多个标签时，会同时计入每个标签的分组。
 */
@Value
public class MetricsSummary {

	/** 评测时使用的 K 值，例如 [5, 10, 20]。 */
	List<Integer> ks;

	/** 全部题目的指标。 */
	GroupMetrics overall;

	/** 按题型标签分组，key 为标签，按字母序排列。 */
	Map<String, GroupMetrics> byTag;

	/** 按难度分组，key 为 L1～L4，按字母序排列。 */
	Map<String, GroupMetrics> byDifficulty;

	/**
	 * 从逐题结果计算汇总指标。
	 * @param results 逐题结果，不能为空
	 * @param ks 要统计的 K 值，不能为空
	 */
	public static MetricsSummary of(List<QuestionResult> results, List<Integer> ks) {
		if (results == null || results.isEmpty()) {
			throw new IllegalArgumentException("results must not be empty");
		}
		if (ks == null || ks.isEmpty()) {
			throw new IllegalArgumentException("ks must not be empty");
		}

		// TreeMap：key 按字母序排列，报告输出顺序稳定，两次评测的报告可以直接 diff
		Map<String, List<QuestionResult>> tagGroups = new TreeMap<>();
		Map<String, List<QuestionResult>> difficultyGroups = new TreeMap<>();
		for (QuestionResult r : results) {
			for (String tag : r.getTags()) {
				tagGroups.computeIfAbsent(tag, key -> new ArrayList<>()).add(r);
			}
			if (r.getDifficulty() != null) {
				difficultyGroups.computeIfAbsent(r.getDifficulty(), key -> new ArrayList<>()).add(r);
			}
		}

		return new MetricsSummary(Collections.unmodifiableList(new ArrayList<>(ks)), GroupMetrics.of(results, ks),
				toMetrics(tagGroups, ks), toMetrics(difficultyGroups, ks));
	}

	private static Map<String, GroupMetrics> toMetrics(Map<String, List<QuestionResult>> groups, List<Integer> ks) {
		Map<String, GroupMetrics> result = new LinkedHashMap<>();
		for (Map.Entry<String, List<QuestionResult>> entry : groups.entrySet()) {
			result.put(entry.getKey(), GroupMetrics.of(entry.getValue(), ks));
		}
		return Collections.unmodifiableMap(result);
	}

	/**
	 * 一组题目的指标。
	 */
	@Value
	public static class GroupMetrics {

		/** 题目数量。 */
		int questionCount;

		/** 平均表召回率，key 为 K。 */
		Map<Integer, Double> tableRecall;

		/** 全命中率（全命中题数 / 题目数），key 为 K。 */
		Map<Integer, Double> fullHitRate;

		/** 召回耗时中位数，毫秒。 */
		long p50Millis;

		/** 召回耗时 95 分位，毫秒。 */
		long p95Millis;

		static GroupMetrics of(List<QuestionResult> results, List<Integer> ks) {
			Map<Integer, Double> recall = new TreeMap<>();
			Map<Integer, Double> fullHit = new TreeMap<>();
			for (int k : ks) {
				double recallSum = 0;
				int hitCount = 0;
				for (QuestionResult r : results) {
					recallSum += r.tableRecallAt(k);
					if (r.fullHitAt(k)) {
						hitCount++;
					}
				}
				recall.put(k, recallSum / results.size());
				fullHit.put(k, (double) hitCount / results.size());
			}

			List<Long> latencies = new ArrayList<>();
			for (QuestionResult r : results) {
				latencies.add(r.getElapsedMillis());
			}
			return new GroupMetrics(results.size(), Collections.unmodifiableMap(recall),
					Collections.unmodifiableMap(fullHit), RecallMetrics.percentile(latencies, 50),
					RecallMetrics.percentile(latencies, 95));
		}

	}

}
