/*
 * Copyright 2026 the original author or authors.
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

import java.util.*;

/**
 * 召回评测指标的纯计算函数，不依赖数据库和模型，便于单元测试。
 * <p>
 * 所有"排名"都指召回结果中的表名顺序：第 1 名最相关。表名比较前统一转小写、去重（保留首次出现的位置）。
 */
public final class RecallMetrics {

	private RecallMetrics() {

	}

	/**
	 * 表召回率@K：前 K 张表中命中了多少张正确的表。
	 * <p>
	 * 一道题可能有多组可接受答案（例如查 ADS 层或从明细聚合都对），对每组分别计算
	 * {@code |前K ∩ 该组| / |该组|}，取最大值。这样选了任意一种正确写法所需的表，都能拿到满分。
	 * @param rankedTables 召回结果，按相关度从高到低
	 * @param acceptableTableSets 可接受的表集合，至少一组
	 * @param k 只看前 k 名，必须大于 0
	 * @return 0 到 1 之间的小数
	 */
	public static double tableRecallAtK(List<String> rankedTables, List<Set<String>> acceptableTableSets, int k) {
		Set<String> topK = topK(rankedTables, k);
		double best = 0;
		for (Set<String> acceptable : requireNonEmpty(acceptableTableSets)) {
			int hit = 0;
			for (String table : acceptable) {
				if (topK.contains(table.toLowerCase(Locale.ROOT))) {
					hit++;
				}
			}
			best = Math.max(best, (double) hit / acceptable.size());
		}
		return best;
	}

	/**
	 * 全命中@K：前 K 张表是否完整覆盖了某一组可接受表集合。
	 * <p>
	 * 这是能否写出正确 SQL 的前提：缺一张表，SQL 就写不对。比召回率更严格，也更能反映真实效果。
	 */
	public static boolean fullHitAtK(List<String> rankedTables, List<Set<String>> acceptableTableSets, int k) {
		Set<String> topK = topK(rankedTables, k);
		for (Set<String> acceptable : requireNonEmpty(acceptableTableSets)) {
			if (topK.containsAll(acceptable)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 最近秩（nearest-rank）百分位数：排序后取第 ⌈p/100 × n⌉ 个值（下标从 1 开始）。
	 * <p>
	 * 例如 10 个值的 P95：⌈0.95 × 10⌉ = 10，取最大值；P50：⌈0.5 × 10⌉ = 5，取第 5 小的值。
	 * 结果一定是样本中真实出现过的值，不做插值。
	 * @param values 样本，不能为空
	 * @param percentile 百分位，范围 (0, 100]
	 */
	public static long percentile(List<Long> values, double percentile) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException("values must not be empty");
		}
		if (percentile <= 0 || percentile > 100) {
			throw new IllegalArgumentException("percentile must be in (0, 100]");
		}
		List<Long> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		int rank = (int) Math.ceil(percentile / 100.0 * sorted.size());
		return sorted.get(rank - 1);
	}

	/** 取前 k 个不重复的表名（小写），保持排名顺序。 */
	static Set<String> topK(List<String> rankedTables, int k) {
		if (k <= 0) {
			throw new IllegalArgumentException("k must be positive");
		}
		Set<String> result = new LinkedHashSet<>();
		if (rankedTables == null)
			return result;
		for (String table : rankedTables) {
			if (result.size() >= k)
				break;
			result.add(table.toLowerCase(Locale.ROOT));
		}
		return result;
	}

	private static List<Set<String>> requireNonEmpty(List<Set<String>> acceptableTableSets) {
		if (acceptableTableSets == null || acceptableTableSets.isEmpty()) {
			throw new IllegalArgumentException("acceptableTableSets must not be empty");
		}
		return acceptableTableSets;
	}

}
