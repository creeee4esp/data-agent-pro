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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.service.schemalink.LinkedSchema;
import com.alibaba.cloud.ai.dataagent.service.schemalink.SchemaLinkRequest;
import com.alibaba.cloud.ai.dataagent.service.schemalink.SchemaLinker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * Schema 召回评测器（S1 Task 6、7）。
 * <p>
 * 流程：读题集 → 手工装配并初始化被评测数据库的 schema（ADR-009）→ 逐题调用 {@link SchemaLinker#link} → 计算指标 → 写出
 * report.json 和 summary.md。默认还会用第二个独立环境重跑一遍，检查结果可复现。
 * <p>
 * 依赖真实基础设施：本地 MySQL（enterprise_dw，由 tools/dataset-gen 生成）和 Embedding 服务（默认本地 Ollama）。
 * 环境不满足时测试会<b>失败</b>并给出原因，不会跳过后显示通过（见 mock-strategy-guide）。
 * <p>
 * 运行（在 DataAgent 根目录）：
 *
 * <pre>
 * .\mvnw.cmd -pl data-agent-management test "-Dtest=SchemaRecallBenchmarkIT" "-Djacoco.skip=true"
 * </pre>
 *
 * 参数见 {@link BenchmarkConfig}，例如 {@code "-Dbenchmark.linker=legacy"}。
 */
@Slf4j
class SchemaRecallBenchmarkIT {

	@Test
	void runBenchmark() throws Exception {
		BenchmarkConfig config = BenchmarkConfig.from(System.getProperties(), System.getenv());
		LocalDateTime startedAt = LocalDateTime.now().withNano(0);
		List<BenchmarkQuestion> questions = QuestionLoader.load(config.getQuestionsFile());

		try (BenchmarkEnvironment env = BenchmarkEnvironment.initialize(config)) {
			SchemaLinker linker = SchemaLinkerRegistry.create(config.getLinker(), env);
			long callsBeforeQueries = env.getEmbeddingModel().getMissCount();
			List<QuestionResult> results = runQuestions(linker, questions);
			long queryCalls = env.getEmbeddingModel().getMissCount() - callsBeforeQueries;

			// 可复现性：用一个全新的环境（新的向量库、重新初始化）再跑一遍，逐题比较召回结果
			Boolean deterministic = null;
			List<String> differences = new ArrayList<>();
			if (config.isVerifyDeterminism()) {
				try (BenchmarkEnvironment second = BenchmarkEnvironment.initialize(config)) {
					List<QuestionResult> rerun = runQuestions(SchemaLinkerRegistry.create(config.getLinker(), second),
							questions);
					differences = QuestionResult.compareRankings(results, rerun);
					deterministic = differences.isEmpty();
				}
			}

			MetricsSummary summary = MetricsSummary.of(results, config.getKs());
			CachingChatModel chat = env.getChatModel();
			BenchmarkRunInfo info = BenchmarkRunInfo.builder()
				.label(config.getLabel())
				.linkerName(config.getLinker())
				.startedAt(startedAt)
				.dataset(config.displayJdbcUrl())
				.tableCount(env.getTableCount())
				.questionsFile(config.getQuestionsFile().toString())
				.questionCount(questions.size())
				.ks(config.getKs())
				.embeddingModel(config.getEmbeddingModel())
				.tableTopK(env.getProperties().getVectorStore().getTableTopkLimit())
				.tableSimilarityThreshold(env.getProperties().getVectorStore().getTableSimilarityThreshold())
				.initMillis(env.getInitMillis())
				.initEmbeddingCalls(env.getInitEmbeddingCalls())
				.queryEmbeddingCalls(queryCalls)
				.avgTables(results.stream().mapToInt(r -> r.getRankedTables().size()).average().orElse(0))
				.chatModel(chat == null ? null : config.getChatModel())
				.chatCalls(chat == null ? 0 : chat.getCalls())
				.chatMisses(chat == null ? 0 : chat.getMisses())
				.promptTokens(chat == null ? 0 : chat.getPromptTokens())
				.completionTokens(chat == null ? 0 : chat.getCompletionTokens())
				.deterministic(deterministic)
				.build();
			Path dir = BenchmarkReportWriter.write(config.getReportDir(), info, summary, results);

			Map<Integer, Double> fullHit = summary.getOverall().getFullHitRate();
			log.info(
					"Benchmark '{}' done: {} questions, full-hit {}, avgTables={}, chat calls={} misses={} tokens={}/{}, deterministic={}, report -> {}",
					config.getLabel(), questions.size(), fullHit, String.format("%.1f", info.getAvgTables()),
					info.getChatCalls(), info.getChatMisses(), info.getPromptTokens(), info.getCompletionTokens(),
					deterministic, dir.toAbsolutePath());

			// 断言评测本身可信、完整（不对召回质量设阈值：这是测量，不是验收）
			assertEquals(questions.size(), results.size());
			assertTrue(differences.isEmpty(), "Recall is not reproducible, rankings differ: " + differences);
			String md = new String(Files.readAllBytes(dir.resolve("summary.md")), StandardCharsets.UTF_8);
			assertTrue(md.contains("| 全部 | " + questions.size() + " |"), md);
			assertTrue(Files.size(dir.resolve("report.json")) > 0);
		}
	}

	/** 逐题调用召回并计时。直接用原问题作为 canonicalQuery，不经过 LLM 改写，排除随机性。 */
	private static List<QuestionResult> runQuestions(SchemaLinker linker, List<BenchmarkQuestion> questions) {
		List<QuestionResult> results = new ArrayList<>();
		for (BenchmarkQuestion q : questions) {
			SchemaLinkRequest request = SchemaLinkRequest.builder()
				.datasourceId(BenchmarkEnvironment.DATASOURCE_ID)
				.canonicalQuery(q.getQuestion())
				.evidence(q.getEvidence())
				.build();
			long start = System.nanoTime();
			LinkedSchema linked = linker.link(request);
			long elapsed = (System.nanoTime() - start) / 1_000_000;
			results.add(QuestionResult.of(q, linked.getTableNames(), elapsed));
		}
		return results;
	}

}
