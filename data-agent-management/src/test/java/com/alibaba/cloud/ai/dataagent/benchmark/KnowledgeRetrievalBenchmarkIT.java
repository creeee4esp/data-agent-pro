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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeResourceManager;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeRetriever;
import com.alibaba.cloud.ai.dataagent.service.knowledge.KnowledgeReranker;
import com.alibaba.cloud.ai.dataagent.service.knowledge.TextSplitterFactory;
import com.alibaba.cloud.ai.dataagent.service.knowledge.chunk.MarkdownConverter;
import com.alibaba.cloud.ai.dataagent.service.knowledge.chunk.ParentChildChunker;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

/**
 * 智能体知识检索评测器（K2）。
 * <p>
 * 流程：读 tools/dataset-gen/knowledge 下的语料和查询 → 用生产的 {@link AgentKnowledgeResourceManager}
 * 写入向量库（文档按 splitterType 切块）→ 逐条查询调用生产的 {@link AgentKnowledgeRetriever}（父子分块时展开成父块）→
 * 计算指标，写出 report.json。
 * <p>
 * 命中判定：文档类目标 = 返回的块属于目标文档且包含标注的关键句；QA / FAQ = 返回的文档就是那一条。 同一次检索取 top 20、不设阈值算排序指标；再截取 "分数
 * ≥ 生产阈值的前 topK 条"算生产配置下的命中率和交给大模型的字数。
 * <p>
 * 依赖：Embedding 服务（默认本地 Ollama）；不需要 MySQL。运行（在 DataAgent 根目录）：
 *
 * <pre>
 * .\mvnw.cmd -pl data-agent-management test "-Dtest=KnowledgeRetrievalBenchmarkIT" "-Djacoco.skip=true"
 * </pre>
 *
 * 参数：{@code -Dbenchmark.knowledge.dir}（默认 ../tools/dataset-gen/knowledge）、
 * {@code -Dbenchmark.knowledge.splitter}（recursive /
 * parent-child）、{@code -Dbenchmark.knowledge.chunk-size}（recursive 用，1000）、
 * {@code -Dbenchmark.knowledge.child-chars}（parent-child
 * 用，400）、{@code -Dbenchmark.knowledge.max-parents}（8）、 {@code -Dbenchmark.label}，以及
 * {@link BenchmarkConfig} 的 Embedding / 向量库参数。
 */
@Slf4j
class KnowledgeRetrievalBenchmarkIT {

	private static final String AGENT_ID = "1";

	private static final int RETRIEVE_TOP_K = 20;

	private static final List<Integer> KS = Collections.unmodifiableList(Arrays.asList(1, 3, 5, 8));

	private static final Pattern NORM = Pattern.compile("[\\s`*]");

	private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	@Test
	void runBenchmark() throws Exception {
		BenchmarkConfig config = BenchmarkConfig.from(System.getProperties(), System.getenv());
		Path dir = Paths.get(System.getProperty("benchmark.knowledge.dir", "../tools/dataset-gen/knowledge"));
		String splitterName = System.getProperty("benchmark.knowledge.splitter", "recursive");
		int chunkSize = Integer.parseInt(System.getProperty("benchmark.knowledge.chunk-size", "1000"));
		String label = System.getProperty("benchmark.label", "knowledge-" + splitterName + "-" + chunkSize);
		LocalDateTime startedAt = LocalDateTime.now().withNano(0);

		List<JsonNode> manifest = readJsonl(dir.resolve("manifest.jsonl"));
		List<JsonNode> qaFaq = readJsonl(dir.resolve("qa_faq.jsonl"));
		List<JsonNode> queries = readJsonl(dir.resolve("queries.jsonl"));
		// 抽样（-Dbenchmark.knowledge.limit=100）：等间隔取，覆盖各类查询
		int limit = Integer.getInteger("benchmark.knowledge.limit", 0);
		if (limit > 0 && limit < queries.size()) {
			List<JsonNode> sample = new ArrayList<>();
			for (int i = 0; i < limit; i++) {
				sample.add(queries.get((int) ((long) i * queries.size() / limit)));
			}
			queries = sample;
		}
		try (BenchmarkEnvironment env = BenchmarkEnvironment.createEmpty(config)) {
			AgentVectorStoreService store = env.getVectorStoreService();

			// 1. 入库：走生产的 AgentKnowledgeResourceManager（文档按 splitterType 切块，QA / FAQ
			// 只向量化问题）。
			// 知识 id 按文件顺序分配（文档 1..N，QA / FAQ 从 100001 起）
			DataAgentProperties.ParentChild pc = env.getProperties().getAgentKnowledge().getParentChild();
			pc.setChildChars(Integer.parseInt(System.getProperty("benchmark.knowledge.child-chars", "400")));
			pc.setMaxParents(Integer.parseInt(System.getProperty("benchmark.knowledge.max-parents", "8")));
			AgentKnowledgeResourceManager manager = new AgentKnowledgeResourceManager(
					new TextSplitterFactory(
							Collections.singletonMap("recursive", new RecursiveCharacterTextSplitter(chunkSize))),
					localFiles(), store, new MarkdownConverter(), new ParentChildChunker(env.getProperties()));
			// 精排（-Dbenchmark.knowledge.rerank=true，需要先启动 tools/reranker/server.py）：
			// 作用于生产配置的检索；候选数 / 阈值 / 保留条数可覆盖，默认 8 / 0.5 / 5
			if (Boolean.getBoolean("benchmark.knowledge.rerank")) {
				DataAgentProperties.Rerank prodRerank = env.getProperties().getAgentKnowledge().getRerank();
				prodRerank.setEnabled(true);
				prodRerank.setCandidates(
						Integer.parseInt(System.getProperty("benchmark.knowledge.rerank-candidates", "8")));
				prodRerank.setMinScore(
						Double.parseDouble(System.getProperty("benchmark.knowledge.rerank-min-score", "0.5")));
				prodRerank.setMaxResults(
						Integer.parseInt(System.getProperty("benchmark.knowledge.rerank-max-results", "5")));
			}
			// 排序精排（-Dbenchmark.knowledge.rerank-ranking=true）：让 R@k、MRR 也反映精排后的顺序。
			// 20 个候选全部打分、不设阈值、不截断；精排是慢操作，只在评测里开
			DataAgentProperties.Rerank rankingRerank = new DataAgentProperties.Rerank();
			if (Boolean.getBoolean("benchmark.knowledge.rerank-ranking")) {
				rankingRerank.setEnabled(true);
				rankingRerank.setCandidates(RETRIEVE_TOP_K);
				rankingRerank.setMinScore(0);
				rankingRerank.setMaxResults(RETRIEVE_TOP_K);
			}
			AgentKnowledgeRetriever retriever = new AgentKnowledgeRetriever(store, env.getProperties());
			AgentKnowledgeRetriever rankRetriever = new AgentKnowledgeRetriever(store, env.getProperties(),
					new KnowledgeReranker(rankingRerank));
			Map<String, Integer> docIds = new HashMap<>();
			Map<String, Integer> qaIds = new HashMap<>();
			long ingestStart = System.nanoTime();
			for (int i = 0; i < manifest.size(); i++) {
				JsonNode m = manifest.get(i);
				AgentKnowledge k = knowledge(i + 1, KnowledgeType.DOCUMENT, m.get("title").asText());
				Path file = dir.resolve(m.get("file").asText()).toAbsolutePath();
				k.setFilePath(file.toString());
				k.setSourceFilename(file.getFileName().toString());
				k.setSplitterType(splitterName);
				docIds.put(m.get("file").asText(), k.getId());
				manager.doEmbedingToVectorStore(k);
			}
			for (int i = 0; i < qaFaq.size(); i++) {
				JsonNode q = qaFaq.get(i);
				KnowledgeType type = "QA".equals(q.get("type").asText()) ? KnowledgeType.QA : KnowledgeType.FAQ;
				AgentKnowledge k = knowledge(100001 + i, type, q.get("title").asText());
				k.setQuestion(q.get("question").asText());
				k.setContent(q.get("answer").asText());
				qaIds.put(q.get("id").asText(), k.getId());
				manager.doEmbedingToVectorStore(k);
			}
			env.refresh();
			long ingestMillis = (System.nanoTime() - ingestStart) / 1_000_000;
			long ingestEmbeddings = env.getEmbeddingModel().getMissCount();
			FilterExpressionBuilder fb = new FilterExpressionBuilder();
			List<Document> stored = store.getDocumentsOnlyByFilter(fb
				.and(fb.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE),
						fb.eq(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, KnowledgeType.DOCUMENT.getCode()))
				.build(), 10_000); // ES 单次最多返回 1 万条（index.max_result_window）
			int chunks = stored.size();
			List<Long> chunkChars = new ArrayList<>();
			stored.forEach(d -> chunkChars.add((long) d.getText().length()));
			log.info("Ingested {} docs as {} chunks + {} QA/FAQ in {} ms ({} new embeddings)", manifest.size(), chunks,
					qaFaq.size(), ingestMillis, ingestEmbeddings);
			// 导出每条查询的前 N 条结果（-Dbenchmark.knowledge.dump-top=8），供复核标注用
			int dumpTop = Integer.getInteger("benchmark.knowledge.dump-top", 0);
			Map<Integer, String> idToFile = new HashMap<>();
			docIds.forEach((file, id) -> idToFile.put(id, file));
			Map<Integer, String> idToQa = new HashMap<>();
			qaIds.forEach((qa, id) -> idToQa.put(id, qa));

			// 2. 逐条检索：同一个 AgentKnowledgeRetriever，排序指标不设阈值、取前 20 组；生产配置用默认参数
			List<Map<String, Object>> rows = new ArrayList<>();
			List<Long> latencies = new ArrayList<>();
			for (JsonNode q : queries) {
				String text = q.get("query").asText();
				long start = System.nanoTime();
				List<Document> prod = retriever.retrieve(AGENT_ID, text);
				latencies.add((System.nanoTime() - start) / 1_000_000);
				List<Document> ranked = rankRetriever.retrieve(AGENT_ID, text, 50, 0.0, RETRIEVE_TOP_K, RETRIEVE_TOP_K,
						Integer.MAX_VALUE);
				Map<String, Object> row = evaluate(q, ranked, prod, docIds, qaIds);
				if (dumpTop > 0) {
					row.put("retrieved", dump(ranked, dumpTop, idToFile, idToQa));
				}
				rows.add(row);
			}
			// 2b. 阈值扫描（-Dbenchmark.knowledge.thresholds=0.4,0.5,...）：生产配置下的命中率与无关查询误召回率
			Map<String, Object> sweep = new LinkedHashMap<>();
			String thresholds = System.getProperty("benchmark.knowledge.thresholds", "");
			for (String part : thresholds.isEmpty() ? new String[0] : thresholds.split(",")) {
				double t = Double.parseDouble(part.trim());
				List<Map<String, Object>> sweepRows = new ArrayList<>();
				for (int i = 0; i < queries.size(); i++) {
					JsonNode q = queries.get(i);
					List<Document> prod = retriever.retrieve(AGENT_ID, q.get("query").asText(), pc.getChildTopK(), t,
							env.getProperties().getVectorStore().getDefaultTopkLimit(), pc.getMaxParents(),
							pc.getContextBudgetChars());
					sweepRows.add(evaluate(q, Collections.emptyList(), prod, docIds, qaIds));
				}
				Map<String, Object> s = summarize(sweepRows);
				Map<String, Object> brief = new LinkedHashMap<>();
				brief.put("productionHit", s.get("productionHit"));
				brief.put("falsePositiveRate", s.get("falsePositiveRate"));
				brief.put("avgContextChars", s.get("avgContextChars"));
				sweep.put(String.valueOf(t), brief);
				log.info("Threshold {} -> {}", t, brief);
			}

			// 3. 汇总
			Map<String, Object> report = new LinkedHashMap<>();
			report.put("label", label);
			report.put("startedAt", startedAt.toString());
			report.put("splitter", splitterName);
			report.put("chunkSize", chunkSize);
			report.put("vectorStore", config.getVectorStore());
			report.put("embeddingModel", config.getEmbeddingModel());
			report.put("documents", manifest.size());
			report.put("qaFaq", qaFaq.size());
			report.put("chunks", chunks);
			report.put("chunkCharsAvg", Math.round(chunkChars.stream().mapToLong(Long::longValue).average().orElse(0)));
			report.put("chunkCharsP50", percentile(chunkChars, 50));
			report.put("chunkCharsP95", percentile(chunkChars, 95));
			report.put("ingestMillis", ingestMillis);
			report.put("queries", queries.size());
			report.put("childChars", pc.getChildChars());
			report.put("production", env.getProperties().getVectorStore().getDefaultTopkLimit() + " items / threshold "
					+ env.getProperties().getVectorStore().getDefaultSimilarityThreshold()
					+ " / parent-child maxParents " + pc.getMaxParents() + ", budget " + pc.getContextBudgetChars());
			DataAgentProperties.Rerank reportRerank = env.getProperties().getAgentKnowledge().getRerank();
			report.put("rerank", reportRerank.isEnabled());
			report.put("rerankConfig", reportRerank.isEnabled()
					? "candidates=" + reportRerank.getCandidates() + " / minScore=" + reportRerank.getMinScore()
							+ " / maxResults=" + reportRerank.getMaxResults()
					: "disabled");
			report.put("rerankRanking", Boolean.getBoolean("benchmark.knowledge.rerank-ranking"));
			report.put("latencyP50Ms", percentile(latencies, 50));
			report.put("latencyP95Ms", percentile(latencies, 95));
			report.put("overall", summarize(rows));
			List<Map<String, Object>> lenient = lenient(rows);
			report.put("overallLenient", summarize(lenient));
			report.put("byTypeLenient", groupBy(lenient, r -> (String) r.get("type")));
			report.put("byDocTypeLenient", groupBy(lenient, r -> (String) r.get("docType")));
			report.put("byType", groupBy(rows, r -> (String) r.get("type")));
			report.put("byDocType", groupBy(rows, r -> (String) r.get("docType")));
			report.put("thresholdSweep", sweep);
			report.put("results", rows);

			Path out = config.getReportDir()
				.resolveSibling("knowledge-benchmark")
				.resolve(startedAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-" + label);
			Files.createDirectories(out);
			mapper.writeValue(out.resolve("report.json").toFile(), report);
			log.info("Knowledge benchmark '{}' done: chunks={}, overall={}, lenient={}, p50={}ms, report -> {}", label,
					chunks, report.get("overall"), report.get("overallLenient"), report.get("latencyP50Ms"),
					out.toAbsolutePath());

			assertEquals(queries.size(), rows.size());
			assertTrue(chunks > manifest.size(), "every document should produce at least one chunk");
		}
	}

	/** 文件就在本地：FileStorageService 只需按路径返回资源。 */
	private static FileStorageService localFiles() {
		return new FileStorageService() {
			@Override
			public Mono<String> storeFile(FilePart filePart, String subPath) {
				throw new UnsupportedOperationException();
			}

			@Override
			public String storeFile(MultipartFile file, String subPath) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean deleteFile(String filePath) {
				return false;
			}

			@Override
			public String getFileUrl(String filePath) {
				return filePath;
			}

			@Override
			public Resource getFileResource(String filePath) {
				return new FileSystemResource(filePath);
			}
		};
	}

	private static AgentKnowledge knowledge(int id, KnowledgeType type, String title) {
		AgentKnowledge k = new AgentKnowledge();
		k.setId(id);
		k.setAgentId(Integer.valueOf(AGENT_ID));
		k.setType(type);
		k.setTitle(title);
		k.setIsRecall(1);
		return k;
	}

	private static Map<String, Object> evaluate(JsonNode q, List<Document> hits, List<Document> prod,
			Map<String, Integer> docIds, Map<String, Integer> qaIds) {
		List<Integer> ranks = new ArrayList<>();
		for (JsonNode t : q.get("targets")) {
			int rank = 0;
			for (int i = 0; i < hits.size(); i++) {
				if (matches(t, hits.get(i), docIds, qaIds)) {
					rank = i + 1;
					break;
				}
			}
			ranks.add(rank);
		}
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", q.get("id").asText());
		row.put("type", q.get("type").asText());
		row.put("docType", q.get("docType").asText());
		row.put("query", q.get("query").asText());
		row.put("ranks", ranks);
		row.put("productionCount", prod.size());
		boolean prodHit = q.get("targets").size() > 0;
		for (JsonNode t : q.get("targets")) {
			prodHit &= prod.stream().anyMatch(d -> matches(t, d, docIds, qaIds));
		}
		row.put("productionHit", prodHit);
		row.put("contextChars", prod.stream().mapToInt(d -> d.getText() == null ? 0 : d.getText().length()).sum());
		row.put("top1Score", hits.isEmpty() ? null : hits.get(0).getScore());
		// 宽松口径：单目标查询加上复核补充的可接受答案（alts），命中任意一个即可；多目标查询与严格口径相同
		List<JsonNode> accepted = new ArrayList<>();
		q.get("targets").forEach(accepted::add);
		if (accepted.size() == 1 && q.has("alts")) {
			q.get("alts").forEach(accepted::add);
			int best = 0;
			for (int i = 0; i < hits.size() && best == 0; i++) {
				for (JsonNode t : accepted) {
					if (matches(t, hits.get(i), docIds, qaIds)) {
						best = i + 1;
						break;
					}
				}
			}
			row.put("lenientRanks", Collections.singletonList(best));
			row.put("lenientProductionHit",
					prod.stream().anyMatch(d -> accepted.stream().anyMatch(t -> matches(t, d, docIds, qaIds))));
		}
		else {
			row.put("lenientRanks", ranks);
			row.put("lenientProductionHit", prodHit);
		}
		return row;
	}

	/** 把每行的 ranks / productionHit 换成宽松口径的值，复用 summarize 计算指标。 */
	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> lenient(List<Map<String, Object>> rows) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map<String, Object> r : rows) {
			Map<String, Object> copy = new LinkedHashMap<>(r);
			copy.put("ranks", r.get("lenientRanks"));
			copy.put("productionHit", r.get("lenientProductionHit"));
			out.add(copy);
		}
		return out;
	}

	/** 前 n 条结果：文档类给 file + 文本，QA / FAQ 给 qa id。 */
	private static List<Map<String, Object>> dump(List<Document> hits, int n, Map<Integer, String> idToFile,
			Map<Integer, String> idToQa) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (int i = 0; i < Math.min(n, hits.size()); i++) {
			Document d = hits.get(i);
			Object raw = d.getMetadata().get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID);
			int id = raw instanceof Number ? ((Number) raw).intValue() : -1;
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("rank", i + 1);
			if (idToQa.containsKey(id)) {
				item.put("kind", "qa");
				item.put("id", idToQa.get(id));
			}
			else {
				item.put("kind", "doc");
				item.put("file", idToFile.get(id));
				item.put("text", d.getText());
			}
			out.add(item);
		}
		return out;
	}

	private static boolean matches(JsonNode target, Document d, Map<String, Integer> docIds,
			Map<String, Integer> qaIds) {
		Object raw = d.getMetadata().get(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID);
		if (!(raw instanceof Number)) {
			return false;
		}
		int id = ((Number) raw).intValue();
		if ("qa".equals(target.get("kind").asText())) {
			return id == qaIds.get(target.get("id").asText());
		}
		Integer docId = docIds.get(target.get("file").asText());
		return docId != null && id == docId && d.getText() != null
				&& norm(d.getText()).contains(norm(target.get("keySentence").asText()));
	}

	static String norm(String s) {
		return NORM.matcher(s).replaceAll("");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> summarize(List<Map<String, Object>> rows) {
		List<Map<String, Object>> answerable = rows.stream()
			.filter(r -> !((List<Integer>) r.get("ranks")).isEmpty())
			.collect(Collectors.toList());
		List<Map<String, Object>> none = rows.stream()
			.filter(r -> ((List<Integer>) r.get("ranks")).isEmpty())
			.collect(Collectors.toList());
		Map<String, Object> s = new LinkedHashMap<>();
		s.put("n", answerable.size());
		if (answerable.isEmpty()) {
			s.put("irrelevantQueries", none.size());
			s.put("falsePositiveRate",
					round(none.stream()
						.mapToDouble(r -> (Integer) r.get("productionCount") > 0 ? 1 : 0)
						.average()
						.orElse(0)));
			return s;
		}
		Map<String, Double> recall = new TreeMap<>();
		Map<String, Double> fullHit = new TreeMap<>();
		for (int k : KS) {
			recall.put("@" + k, round(answerable.stream().mapToDouble(r -> {
				List<Integer> ranks = (List<Integer>) r.get("ranks");
				return ranks.stream().filter(x -> x > 0 && x <= k).count() / (double) ranks.size();
			}).average().orElse(0)));
			fullHit.put("@" + k, round(answerable.stream()
				.mapToDouble(r -> ((List<Integer>) r.get("ranks")).stream().allMatch(x -> x > 0 && x <= k) ? 1 : 0)
				.average()
				.orElse(0)));
		}
		s.put("recall", recall);
		s.put("fullHit", fullHit);
		s.put("mrr", round(answerable.stream().mapToDouble(r -> {
			int best = ((List<Integer>) r.get("ranks")).stream().filter(x -> x > 0).min(Integer::compare).orElse(0);
			return best == 0 ? 0 : 1.0 / best;
		}).average().orElse(0)));
		s.put("productionHit", round(
				answerable.stream().mapToDouble(r -> (Boolean) r.get("productionHit") ? 1 : 0).average().orElse(0)));
		s.put("avgContextChars",
				Math.round(answerable.stream().mapToInt(r -> (Integer) r.get("contextChars")).average().orElse(0)));
		if (!none.isEmpty()) {
			s.put("irrelevantQueries", none.size());
			s.put("falsePositiveRate",
					round(none.stream()
						.mapToDouble(r -> (Integer) r.get("productionCount") > 0 ? 1 : 0)
						.average()
						.orElse(0)));
		}
		return s;
	}

	private static Map<String, Object> groupBy(List<Map<String, Object>> rows,
			Function<Map<String, Object>, String> key) {
		Map<String, List<Map<String, Object>>> groups = rows.stream()
			.collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList()));
		Map<String, Object> out = new LinkedHashMap<>();
		groups.forEach((k, v) -> out.put(k, summarize(v)));
		return out;
	}

	private static double round(double v) {
		return Math.round(v * 1000) / 1000.0;
	}

	private static long percentile(List<Long> values, int p) {
		List<Long> sorted = new ArrayList<>(values);
		Collections.sort(sorted);
		return sorted.isEmpty() ? 0
				: sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
	}

	private List<JsonNode> readJsonl(Path file) throws IOException {
		List<JsonNode> out = new ArrayList<>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			if (!line.trim().isEmpty()) {
				out.add(mapper.readTree(line));
			}
		}
		return out;
	}

}
