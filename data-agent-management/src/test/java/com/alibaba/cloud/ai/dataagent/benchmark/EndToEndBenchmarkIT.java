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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.EVIDENCE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.MULTI_TURN_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeRetriever;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.StreamLlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.schemalink.LinkedSchema;
import com.alibaba.cloud.ai.dataagent.service.schemalink.SchemaLinkRequest;
import com.alibaba.cloud.ai.dataagent.service.schemalink.SchemaLinker;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.JoinPathEnricher;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.JoinPlan;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.SchemaGraphCache;
import com.alibaba.cloud.ai.dataagent.support.GraphNodeTestSupport;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.workflow.node.EvidenceRecallNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * 端到端评测：召回 → 补外键 → 大模型精选表（fineSelect）→ 生成 SQL → 执行 → 与 gold / alt_sqls 的执行结果比较。
 * <p>
 * 除召回外，各步都直接调用生产代码（{@code SchemaService#buildSchemaFromDocuments}、{@link Nl2SqlService}）。
 * 省略意图识别、问题改写、规划、语义一致性检查等节点：直接用原问题，题集的 evidence 作为业务口径。SQL 执行报错时，用生产的修复 prompt 重试
 * {@value #MAX_FIX} 次。
 * <p>
 * 运行（需要对话模型，缓存命中时不计费）：
 *
 * <pre>
 * .\mvnw.cmd -pl data-agent-management test "-Dtest=EndToEndBenchmarkIT" "-Djacoco.skip=true"
 *   "-Dbenchmark.linker=agentic" "-Dbenchmark.schema=enterprise_dw_m" "-Dbenchmark.chat.model=deepseek-flash" ...
 * </pre>
 */
@Slf4j
class EndToEndBenchmarkIT {

	/** 执行报错后的修复次数。 */
	static final int MAX_FIX = 1;

	/** 证据召回用的智能体 id：知识语料按这个 agentId 写入。 */
	private static final String EVIDENCE_AGENT_ID = "1";

	private static final DateTimeFormatter DIR_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	@Test
	void runEndToEnd() throws Exception {
		BenchmarkConfig config = BenchmarkConfig.from(System.getProperties(), System.getenv());
		LocalDateTime startedAt = LocalDateTime.now().withNano(0);
		List<BenchmarkQuestion> questions = QuestionLoader.load(config.getQuestionsFile());

		try (BenchmarkEnvironment env = BenchmarkEnvironment.initialize(config)) {
			SchemaLinker linker = SchemaLinkerRegistry.create(config.getLinker(), env);
			LlmService llm = new StreamLlmService(env.getAiModelRegistry());
			Nl2SqlService nl2Sql = new Nl2SqlServiceImpl(llm, new JsonParseUtil(llm));
			DbConfigBO dbConfig = env.getDatabaseUtil().getAgentDbConfig(null);
			Accessor accessor = env.getDatabaseUtil().getAgentAccessor(null);
			// 外键多跳补全（-Dbenchmark.join-graph=true）：与生产 TableRelationNode 一样在精选后调用
			// JoinPathEnricher；
			// 数据集没有逻辑关系，DatasourceService 只需要返回空列表
			JoinPathEnricher enricher = null;
			if (Boolean.getBoolean("benchmark.join-graph")) {
				env.getProperties().getSchemaLinking().getJoinGraph().setEnabled(true);
				DatasourceService datasourceService = mock(DatasourceService.class);
				when(datasourceService.getLogicalRelations(BenchmarkEnvironment.DATASOURCE_ID))
					.thenReturn(Collections.emptyList());
				enricher = new JoinPathEnricher(
						new SchemaGraphCache(env.getVectorStoreService(), datasourceService, env.getProperties()),
						env.getSchemaService(), env.getProperties());
			}
			// 证据召回（-Dbenchmark.evidence-recall=true）：把知识语料写进向量库，每题调用生产的
			// EvidenceRecallNode
			// （大模型改写问题 → 检索业务知识和智能体知识 → 格式化），结果作为精选表和生成 SQL 的 evidence
			EvidenceRecallNode evidenceNode = null;
			if (Boolean.getBoolean("benchmark.evidence-recall")) {
				Path corpus = Paths
					.get(System.getProperty("benchmark.knowledge.dir", "../tools/dataset-gen/knowledge"));
				KnowledgeCorpusLoader corpusLoader = KnowledgeCorpusLoader.load(env, corpus, EVIDENCE_AGENT_ID);
				evidenceNode = new EvidenceRecallNode(llm, env.getVectorStoreService(), new JsonParseUtil(llm),
						corpusLoader.mapper(),
						new AgentKnowledgeRetriever(env.getVectorStoreService(), env.getProperties()));
			}

			List<Map<String, Object>> rows = new ArrayList<>();
			for (BenchmarkQuestion q : questions) {
				rows.add(runQuestion(q, linker, env, nl2Sql, dbConfig, accessor, enricher, evidenceNode));
			}

			Map<String, Object> summary = summarize(rows, questions);
			CachingChatModel chat = env.getChatModel();
			Map<String, Object> run = new LinkedHashMap<>();
			run.put("label", config.getLabel());
			run.put("linker", config.getLinker());
			run.put("joinGraph", enricher != null);
			run.put("evidenceRecall", evidenceNode != null);
			run.put("dataset", config.displayJdbcUrl());
			run.put("tables", env.getTableCount());
			run.put("questions", questions.size());
			run.put("chatModel", config.getChatModel());
			run.put("chatCalls", chat == null ? 0 : chat.getCalls());
			run.put("chatMisses", chat == null ? 0 : chat.getMisses());
			run.put("promptTokens", chat == null ? 0 : chat.getPromptTokens());
			run.put("completionTokens", chat == null ? 0 : chat.getCompletionTokens());
			Path dir = write(config.getReportDir().resolveSibling("e2e-benchmark"), startedAt, config.getLabel(), run,
					summary, rows);
			log.info("End-to-end '{}' done: {} -> {}", config.getLabel(), summary.get("overall"), dir.toAbsolutePath());
			assertEquals(questions.size(), rows.size());
		}
	}

	private static Map<String, Object> runQuestion(BenchmarkQuestion q, SchemaLinker linker, BenchmarkEnvironment env,
			Nl2SqlService nl2Sql, DbConfigBO dbConfig, Accessor accessor, JoinPathEnricher enricher,
			EvidenceRecallNode evidenceNode) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("id", q.getId());
		row.put("tags", q.getTags());
		long start = System.nanoTime();
		String stage = "recall";
		try {
			// ① 召回
			LinkedSchema linked = linker.link(SchemaLinkRequest.builder()
				.datasourceId(BenchmarkEnvironment.DATASOURCE_ID)
				.canonicalQuery(q.getQuestion())
				.evidence(q.getEvidence())
				.build());
			row.put("recallFullHit", coversAny(q, linked.getTableNames()));

			// ①b 证据召回：题集自带的 evidence 在前，召回的知识在后
			stage = "evidence";
			String evidence = q.getEvidence() == null ? "" : q.getEvidence();
			if (evidenceNode != null) {
				String recalled = recallEvidence(evidenceNode, q.getQuestion());
				row.put("recalledEvidence", recalled);
				if (!recalled.isEmpty() && !"无".equals(recalled)) {
					evidence = evidence.isEmpty() ? recalled : evidence + "\n" + recalled;
				}
			}
			// ② 补外键关联的表，组装 SchemaDTO（与 TableRelationNode 相同）
			stage = "fineSelect";
			SchemaDTO schema = new SchemaDTO();
			env.getSchemaService()
				.buildSchemaFromDocuments(null, new ArrayList<>(linked.getColumnDocuments()),
						new ArrayList<>(linked.getTableDocuments()), schema);

			// ③ 大模型精选表
			SchemaDTO[] selected = { schema };
			nl2Sql.fineSelect(schema, q.getQuestion(), evidence, null, dbConfig, dto -> selected[0] = dto).blockLast();
			List<String> selectedTables = tableNames(selected[0]);
			row.put("selectedTables", selectedTables);
			row.put("selectFullHit", coversAny(q, selectedTables));

			// ③b 外键多跳补全：补中间表、写 JOIN 提示
			if (enricher != null) {
				JoinPlan plan = enricher.enrich(BenchmarkEnvironment.DATASOURCE_ID, selected[0]);
				row.put("addedTables", plan == null ? Collections.emptyList() : plan.getAddedTables());
				row.put("joinHints", selected[0].getJoinHints());
				row.put("enrichedFullHit", coversAny(q, tableNames(selected[0])));
			}
			// ④ 生成 SQL；⑤ 执行，报错时修复
			stage = "generate";
			String sql = generate(nl2Sql, q, evidence, selected[0], dbConfig, null, null);
			ResultSetBO result = null;
			String error = null;
			for (int attempt = 0; attempt <= MAX_FIX; attempt++) {
				if (attempt > 0) {
					sql = generate(nl2Sql, q, evidence, selected[0], dbConfig, sql, error);
				}
				error = readOnlyViolation(sql);
				if (error == null) {
					try {
						result = execute(accessor, dbConfig, sql);
						error = result.getErrorMsg();
					}
					catch (Exception e) {
						error = e.getMessage();
					}
				}
				if (error == null || error.isEmpty()) {
					error = null;
					break;
				}
				result = null;
			}
			row.put("sql", sql);
			row.put("execError", error);

			// ⑥ 比较
			stage = error != null ? "execute" : "compare";
			ResultComparator.Match match = error == null ? bestMatch(q, result, accessor, dbConfig)
					: new ResultComparator.Match(false, false, null);
			boolean ex = match.relaxed;
			row.put("ex", ex);
			row.put("exStrict", match.strict);
			row.put("exApprox", match.approx);
			row.put("columnPrecision", match.columnPrecision);
			row.put("failStage", ex ? null : failStage(row, stage));
		}
		catch (RuntimeException e) {
			row.put("ex", false);
			row.put("failStage", stage);
			row.put("exception", e.getClass().getSimpleName() + ": " + e.getMessage());
			log.warn("Question {} failed at {}: {}", q.getId(), stage, e.toString());
		}
		row.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000);
		return row;
	}

	/** 失败归因：召回漏表 → 精选删表 → 执行报错 → SQL 写错。 */
	private static String failStage(Map<String, Object> row, String stage) {
		if (Boolean.FALSE.equals(row.get("recallFullHit"))) {
			return "recall";
		}
		if (Boolean.FALSE.equals(row.get("selectFullHit")) && !Boolean.TRUE.equals(row.get("enrichedFullHit"))) {
			return "fineSelect";
		}
		return "execute".equals(stage) ? "execute" : "wrongSql";
	}

	/** 调用生产的证据召回节点，取它最终写进状态的 evidence。 */
	private static String recallEvidence(EvidenceRecallNode node, String question) {
		OverAllState state = new OverAllState();
		for (String key : new String[] { INPUT_KEY, AGENT_ID, MULTI_TURN_CONTEXT, EVIDENCE }) {
			state.registerKeyAndStrategy(key, new ReplaceStrategy());
		}
		Map<String, Object> input = new HashMap<>();
		input.put(INPUT_KEY, question);
		input.put(AGENT_ID, EVIDENCE_AGENT_ID);
		state.updateState(input);
		Object evidence;
		try {
			evidence = GraphNodeTestSupport.execute(node.apply(state), EVIDENCE).finalResult().get(EVIDENCE);
		}
		catch (Exception e) {
			throw new IllegalStateException("Evidence recall failed: " + e.getMessage(), e);
		}
		return evidence == null ? "" : evidence.toString();
	}

	private static String generate(Nl2SqlService nl2Sql, BenchmarkQuestion q, String evidence, SchemaDTO schema,
			DbConfigBO dbConfig, String previousSql, String error) {
		SqlGenerationDTO dto = SqlGenerationDTO.builder()
			.query(q.getQuestion())
			.evidence(evidence == null || evidence.isEmpty() ? "无" : evidence)
			.schemaDTO(schema)
			.executionDescription(q.getQuestion())
			.dialect(dbConfig.getDialectType())
			.sql(previousSql)
			.exceptionMessage(error)
			.build();
		String text = String.join("", nl2Sql.generateSql(dto).collectList().block());
		return nl2Sql.sqlTrim(text);
	}

	/** 与 gold 及每条 alt_sql 比较，取最好的结果：严格匹配优先，其次宽松匹配中列精确率最高的。 */
	private static ResultComparator.Match bestMatch(BenchmarkQuestion q, ResultSetBO actual, Accessor accessor,
			DbConfigBO dbConfig) {
		List<String> answers = new ArrayList<>();
		answers.add(q.getGoldSql());
		answers.addAll(q.getAltSqls());
		ResultComparator.Match best = new ResultComparator.Match(false, false, null);
		for (String answer : answers) {
			try {
				ResultComparator.Match m = ResultComparator.compare(execute(accessor, dbConfig, answer), actual,
						q.isOrderMatters());
				if (m.strict) {
					return m;
				}
				if (m.relaxed && (!best.relaxed || m.columnPrecision > best.columnPrecision)) {
					best = m;
				}
				else if (!best.relaxed && m.approx && !best.approx) {
					best = m;
				}
			}
			catch (Exception e) {
				// 某档数据没有替代答案用到的表（例如只在 medium/large 存在的 DWS 表）
				log.debug("Answer SQL failed for {}: {}", q.getId(), e.getMessage());
			}
		}
		return best;
	}

	private static ResultSetBO execute(Accessor accessor, DbConfigBO dbConfig, String sql) throws Exception {
		ResultSetBO result = accessor.executeSqlAndReturnObject(dbConfig,
				DbQueryParameter.from(dbConfig).setSchema(dbConfig.getSchema()).setSql(sql));
		if (result != null && result.getErrorMsg() != null && !result.getErrorMsg().isEmpty()) {
			throw new IllegalStateException(result.getErrorMsg());
		}
		return result;
	}

	/** 只执行单条 SELECT。 */
	static String readOnlyViolation(String sql) {
		try {
			List<SQLStatement> statements = SQLUtils.parseStatements(sql, DbType.mysql);
			if (statements.size() == 1 && statements.get(0) instanceof SQLSelectStatement) {
				return null;
			}
			return "Only a single SELECT statement is allowed";
		}
		catch (RuntimeException e) {
			return "SQL parse error: " + e.getMessage();
		}
	}

	private static boolean coversAny(BenchmarkQuestion q, List<String> tables) {
		Set<String> lower = new HashSet<>();
		for (String t : tables) {
			lower.add(t.toLowerCase(Locale.ROOT));
		}
		for (Set<String> acceptable : q.getAcceptableTableSets()) {
			boolean all = true;
			for (String t : acceptable) {
				all &= lower.contains(t.toLowerCase(Locale.ROOT));
			}
			if (all) {
				return true;
			}
		}
		return false;
	}

	private static List<String> tableNames(SchemaDTO schema) {
		List<String> names = new ArrayList<>();
		if (schema != null && schema.getTable() != null) {
			for (TableDTO table : schema.getTable()) {
				names.add(table.getName());
			}
		}
		return names;
	}

	private static Map<String, Object> summarize(List<Map<String, Object>> rows, List<BenchmarkQuestion> questions) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("overall", rates(rows));
		Map<String, List<Map<String, Object>>> byTag = new TreeMap<>();
		for (Map<String, Object> row : rows) {
			@SuppressWarnings("unchecked")
			List<String> tags = (List<String>) row.get("tags");
			for (String tag : tags) {
				byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(row);
			}
		}
		Map<String, Object> tagRates = new LinkedHashMap<>();
		for (Map.Entry<String, List<Map<String, Object>>> e : byTag.entrySet()) {
			tagRates.put(e.getKey(), rates(e.getValue()));
		}
		summary.put("byTag", tagRates);
		Map<String, Integer> stages = new TreeMap<>();
		for (Map<String, Object> row : rows) {
			Object stage = row.get("failStage");
			if (stage != null) {
				stages.merge(stage.toString(), 1, Integer::sum);
			}
		}
		summary.put("failStages", stages);
		return summary;
	}

	private static Map<String, Object> rates(List<Map<String, Object>> rows) {
		Map<String, Object> r = new LinkedHashMap<>();
		r.put("questions", rows.size());
		r.put("ex", rate(rows, "ex"));
		r.put("exStrict", rate(rows, "exStrict"));
		r.put("exApprox", rate(rows, "exApprox"));
		r.put("columnPrecision", avgPrecision(rows));
		r.put("recallFullHit", rate(rows, "recallFullHit"));
		r.put("selectFullHit", rate(rows, "selectFullHit"));
		if (rows.stream().anyMatch(row -> row.containsKey("enrichedFullHit"))) {
			r.put("enrichedFullHit", rate(rows, "enrichedFullHit"));
		}
		return r;
	}

	/** 宽松匹配的题上，列精确率的平均值（百分比）。 */
	private static double avgPrecision(List<Map<String, Object>> rows) {
		double sum = 0;
		int n = 0;
		for (Map<String, Object> row : rows) {
			Object p = row.get("columnPrecision");
			if (p instanceof Double) {
				sum += (Double) p;
				n++;
			}
		}
		return n == 0 ? 0 : Math.round(sum * 1000.0 / n) / 10.0;
	}

	private static double rate(List<Map<String, Object>> rows, String key) {
		if (rows.isEmpty()) {
			return 0;
		}
		long hit = rows.stream().filter(row -> Boolean.TRUE.equals(row.get(key))).count();
		return Math.round(hit * 1000.0 / rows.size()) / 10.0;
	}

	private static Path write(Path root, LocalDateTime startedAt, String label, Map<String, Object> run,
			Map<String, Object> summary, List<Map<String, Object>> rows) throws Exception {
		Path dir = root.resolve(DIR_TIME.format(startedAt) + "-" + label);
		Files.createDirectories(dir);
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("run", run);
		report.put("summary", summary);
		report.put("questions", rows);
		new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
			.writeValue(dir.resolve("report.json").toFile(), report);

		StringBuilder md = new StringBuilder("# 端到端评测：").append(label).append("\n\n");
		for (Map.Entry<String, Object> e : run.entrySet()) {
			md.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
		}
		md.append(
				"\nEX = 允许多出来的列；EX(严格) = 列也必须完全一致；EX(近似) = 在 EX 基础上不比较行顺序、数值允许 0.1% 相对误差；列精确率 = 标准答案列数 / 结果列数（宽松匹配的题上取平均）。\n");
		md.append("\n| 分组 | 题数 | EX | EX(严格) | EX(近似) | 列精确率 | 召回全命中 | 精选后全命中 |\n|---|---|---|---|---|---|---|---|\n");
		appendRow(md, "全部", summary.get("overall"));
		@SuppressWarnings("unchecked")
		Map<String, Object> byTag = (Map<String, Object>) summary.get("byTag");
		for (Map.Entry<String, Object> e : byTag.entrySet()) {
			appendRow(md, e.getKey(), e.getValue());
		}
		md.append("\n失败归因：").append(summary.get("failStages")).append('\n');
		Files.write(dir.resolve("summary.md"), md.toString().getBytes(StandardCharsets.UTF_8));
		return dir;
	}

	@SuppressWarnings("unchecked")
	private static void appendRow(StringBuilder md, String name, Object value) {
		Map<String, Object> r = (Map<String, Object>) value;
		md.append("| ")
			.append(name)
			.append(" | ")
			.append(r.get("questions"))
			.append(" | ")
			.append(r.get("ex"))
			.append("% | ")
			.append(r.get("exStrict"))
			.append("% | ")
			.append(r.get("exApprox"))
			.append("% | ")
			.append(r.get("columnPrecision"))
			.append("% | ")
			.append(r.get("recallFullHit"))
			.append("% | ")
			.append(r.get("selectFullHit"))
			.append("% |\n");
	}

}
