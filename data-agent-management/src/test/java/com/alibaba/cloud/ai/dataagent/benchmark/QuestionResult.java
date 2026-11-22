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

import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 一道题的评测结果：召回出的表、这道题的可接受答案、耗时，以及计算指标的便捷方法。
 * <p>
 * 由评测器（Task 6）在每道题跑完 {@code SchemaLinker.link} 后创建。
 */
@Value
public class QuestionResult {

	/** 题号。 */
	String questionId;

	/** 难度：L1～L4。 */
	String difficulty;

	/** 题型标签。 */
	List<String> tags;

	/** 召回结果中的表名，按相关度从高到低。 */
	List<String> rankedTables;

	/** 这道题的可接受表集合（来自 QuestionLoader）。 */
	List<Set<String>> acceptableTableSets;

	/** 本题召回耗时，毫秒。 */
	long elapsedMillis;

	/** 由题目和召回结果构造，避免调用方逐个拆字段。 */
	public static QuestionResult of(BenchmarkQuestion question, List<String> rankedTables, long elapsedMillis) {
		return new QuestionResult(question.getId(), question.getDifficulty(), question.getTags(), rankedTables,
				question.getAcceptableTableSets(), elapsedMillis);
	}

	public double tableRecallAt(int k) {
		return RecallMetrics.tableRecallAtK(rankedTables, acceptableTableSets, k);
	}

	public boolean fullHitAt(int k) {
		return RecallMetrics.fullHitAtK(rankedTables, acceptableTableSets, k);
	}

	/**
	 * 比较两次运行的召回顺序（耗时不参与比较），用于可复现性检查。
	 * @return 不一致的题目描述；完全一致时为空列表
	 * @throws IllegalArgumentException 两次运行的题目数量或顺序不同
	 */
	public static List<String> compareRankings(List<QuestionResult> first, List<QuestionResult> second) {
		if (first.size() != second.size()) {
			throw new IllegalArgumentException("result sizes differ: " + first.size() + " vs " + second.size());
		}
		List<String> differences = new ArrayList<>();
		for (int i = 0; i < first.size(); i++) {
			QuestionResult a = first.get(i);
			QuestionResult b = second.get(i);
			if (!a.getQuestionId().equals(b.getQuestionId())) {
				throw new IllegalArgumentException("question order differs at " + i);
			}
			if (!a.getRankedTables().equals(b.getRankedTables())) {
				differences.add(a.getQuestionId() + ": " + a.getRankedTables() + " vs " + b.getRankedTables());
			}
		}
		return differences;
	}

}
