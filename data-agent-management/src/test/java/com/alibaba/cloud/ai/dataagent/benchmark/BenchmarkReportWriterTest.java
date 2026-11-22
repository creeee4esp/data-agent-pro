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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchmarkReportWriterTest {

	private static final List<Integer> KS = Arrays.asList(1, 2);

	@TempDir
	Path root;

	@Test
	void write_createsTimestampedDirectoryWithBothFiles() throws IOException {
		List<QuestionResult> results = sampleResults();

		Path dir = BenchmarkReportWriter.write(root, info(), MetricsSummary.of(results, KS), results);

		assertEquals(root.resolve("20261015-153000-unit"), dir);
		assertTrue(Files.exists(dir.resolve("report.json")));
		assertTrue(Files.exists(dir.resolve("summary.md")));
	}

	@Test
	void reportJson_containsRunSummaryAndPerQuestionMetrics() throws IOException {
		List<QuestionResult> results = sampleResults();
		Path dir = BenchmarkReportWriter.write(root, info(), MetricsSummary.of(results, KS), results);

		JsonNode json = new ObjectMapper().readTree(dir.resolve("report.json").toFile());

		assertEquals("legacy", json.path("run").path("linkerName").asText());
		assertEquals("2026-10-15T15:30:00", json.path("run").path("startedAt").asText());
		assertEquals(0.5, json.path("summary").path("overall").path("fullHitRate").path("2").asDouble(), 1e-9);
		JsonNode q1 = json.path("questions").get(0);
		assertEquals("q1", q1.path("id").asText());
		assertTrue(q1.path("fullHit").path("2").asBoolean());
		assertEquals(0.5, q1.path("tableRecall").path("1").asDouble(), 1e-9);
	}

	@Test
	void renderMarkdown_overallRowAndFailedQuestions() {
		List<QuestionResult> results = sampleResults();

		String md = BenchmarkReportWriter.renderMarkdown(info(), MetricsSummary.of(results, KS), results);

		// 列顺序：题数 | 全命中@1 | 全命中@2 | 召回率@1 | 召回率@2 | P50 | P95
		assertTrue(md.contains("| 全部 | 2 | 0.0% | 50.0% | 25.0% | 50.0% | 10 | 30 |"), md);
		assertTrue(md.contains("| metric | 1 |"), md);
		// q2 在 K=2 没有全命中，出现在失败清单；q1 不出现
		assertTrue(md.contains("- **q2** 召回率 0.0%"), md);
		assertFalse(md.contains("- **q1**"), md);
	}

	@Test
	void pct_usesDotDecimalSeparator() {
		assertEquals("83.3%", BenchmarkReportWriter.pct(5.0 / 6));
	}

	@Test
	void config_defaultsAndOverrides() {
		Map<String, String> props = new HashMap<>();
		props.put("benchmark.label", "multi-route");
		props.put("benchmark.ks", "20, 5,10");

		BenchmarkConfig config = BenchmarkConfig.from(props, Collections.singletonMap("BENCHMARK_DB_PASSWORD", "s3"));

		assertEquals("multi-route", config.getLabel());
		assertEquals(Arrays.asList(5, 10, 20), config.getKs()); // 排序后
		assertEquals("s3", config.getDbPassword()); // 环境变量优先
		assertEquals(Paths.get("../tools/dataset-gen/questions/m0.jsonl"), config.getQuestionsFile());
		assertEquals("jdbc:mysql://127.0.0.1:3306/enterprise_dw", config.displayJdbcUrl());
	}

	@Test
	void config_nonPositiveK_isRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> BenchmarkConfig.from(Collections.singletonMap("benchmark.ks", "0,5"), Collections.emptyMap()));
	}

	@Test
	void summaryMd_isUtf8() throws IOException {
		List<QuestionResult> results = sampleResults();
		Path dir = BenchmarkReportWriter.write(root, info(), MetricsSummary.of(results, KS), results);

		String md = new String(Files.readAllBytes(dir.resolve("summary.md")), StandardCharsets.UTF_8);

		assertTrue(md.startsWith("# Schema 召回评测：unit"), md);
	}

	/** q1：2 张表都在前 2 名；q2：一张都没召回到。 */
	private static List<QuestionResult> sampleResults() {
		return Arrays.asList(
				new QuestionResult("q1", "L2", Collections.singletonList("metric"), Arrays.asList("a", "b"),
						Collections.singletonList(setOf("a", "b")), 10),
				new QuestionResult("q2", "L1", Collections.singletonList("value_match"), Arrays.asList("x"),
						Collections.singletonList(setOf("c")), 30));
	}

	private static BenchmarkRunInfo info() {
		return BenchmarkRunInfo.builder()
			.label("unit")
			.linkerName("legacy")
			.startedAt(LocalDateTime.of(2026, 10, 15, 15, 30, 0))
			.dataset("jdbc:mysql://127.0.0.1:3306/enterprise_dw")
			.tableCount(44)
			.questionsFile("m0.jsonl")
			.questionCount(2)
			.ks(KS)
			.embeddingModel("fake")
			.tableTopK(10)
			.tableSimilarityThreshold(0.2)
			.build();
	}

	private static Set<String> setOf(String... values) {
		return new HashSet<>(Arrays.asList(values));
	}

}
