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

import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.EdgeType;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.JoinPathResolver;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.JoinPlan;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.SchemaEdge;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.SchemaGraph;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.SchemaGraphCache;
import com.alibaba.cloud.ai.dataagent.service.schemalink.graph.TableNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S4-1 建图评测：用真实 schema 走生产初始化路径写入向量库，再调用 {@link SchemaGraphCache} 建一次全量表关系图，
 * 记录建图耗时、各类边数和缓存命中耗时，并检查边的正确性：
 * <ul>
 * <li>召回：数据集生成时的干净外键（{@code clean_metadata.json} 的 {@code foreign_keys}，退化时删掉了一部分）有多少在图里，
 * 被删掉的那部分有多少由命名推断找回；</li>
 * <li>合理性：命名推断边至少有一端的列是该表的主键（"引用实体表"），否则大概率是两张事实表碰巧同名的列；</li>
 * <li>核心表之间的命名推断边，不在干净外键里的逐条列出。</li>
 * </ul>
 * 全部边写到 {@code target/schema-graph/<时间>-<schema>/edges.json} 供人工检查。
 * <p>
 * 依赖真实基础设施：MySQL（enterprise_dw_l，由 tools/dataset-gen 生成）、Embedding 服务（默认本地 Ollama）、生成器输出的
 * {@code clean_metadata.json}（{@code -Dbenchmark.graph.clean-metadata=}
 * 覆盖）。环境不满足时测试失败并给出原因，不跳过。
 * <p>
 * 运行（DataAgent 根目录）：
 *
 * <pre>
 * .\mvnw.cmd -pl data-agent-management test "-Dtest=SchemaGraphBenchmarkIT" "-Dbenchmark.schema=enterprise_dw_l" ^
 *     "-Djacoco.skip=true"
 * </pre>
 */
@Slf4j
class SchemaGraphBenchmarkIT {

	private static final Map<String, String> OUT_DIRS = new HashMap<>();

	static {
		OUT_DIRS.put("enterprise_dw_s", "s2-small");
		OUT_DIRS.put("enterprise_dw_m", "s2-medium");
		OUT_DIRS.put("enterprise_dw_l", "s2-large");
	}

	@Test
	void buildsGraphForWholeSchema() throws Exception {
		BenchmarkConfig config = BenchmarkConfig.from(System.getProperties(), System.getenv());
		Path cleanMetadata = Paths.get(System.getProperty("benchmark.graph.clean-metadata", "../tools/dataset-gen/out/"
				+ OUT_DIRS.getOrDefault(config.getSchemaName(), "") + "/clean_metadata.json"));
		assertTrue(Files.exists(cleanMetadata),
				"clean metadata not found (generate the dataset first): " + cleanMetadata.toAbsolutePath());
		ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
		JsonNode clean = mapper.readTree(cleanMetadata.toFile());

		try (BenchmarkEnvironment env = BenchmarkEnvironment.initialize(config)) {
			// 数据集没有逻辑关系，返回空列表即可；其他方法在本次评测里不会被调用
			DatasourceService datasourceService = mock(DatasourceService.class);
			when(datasourceService.getLogicalRelations(BenchmarkEnvironment.DATASOURCE_ID))
				.thenReturn(Collections.emptyList());
			SchemaGraphCache cache = new SchemaGraphCache(env.getVectorStoreService(), datasourceService,
					env.getProperties());

			// 冷启动：两次向量库查询（表 + 列分批）+ 建图
			long start = System.nanoTime();
			SchemaGraph graph = cache.get(BenchmarkEnvironment.DATASOURCE_ID);
			long coldMillis = (System.nanoTime() - start) / 1_000_000;

			// 命中缓存：不做任何向量库查询，直接返回同一个对象
			long hitStart = System.nanoTime();
			SchemaGraph hit = cache.get(BenchmarkEnvironment.DATASOURCE_ID);
			long hitMicros = (System.nanoTime() - hitStart) / 1_000;

			int representatives = 0;
			for (TableNode node : graph.getTables().values()) {
				representatives += node.isRepresentative() ? 1 : 0;
			}
			log.info(
					"S4-1 graph ({}): tables={} (representative={}), edges={} (fk={}, logical={}, naming={}), "
							+ "skipped={}, cold={} ms, cacheHit={} us",
					config.getSchemaName(), graph.getTables().size(), representatives, graph.edgeCount(),
					graph.edgeCount(EdgeType.FOREIGN_KEY), graph.edgeCount(EdgeType.LOGICAL_RELATION),
					graph.edgeCount(EdgeType.NAMING_INFERENCE), graph.getSkippedEdgeCount(), coldMillis, hitMicros);

			Map<String, Object> report = check(graph, clean, primaryKeys(config));
			report.put("joinPaths", checkJoinPaths(graph, QuestionLoader.load(config.getQuestionsFile())));
			report.put("tables", graph.getTables().size());
			report.put("representativeTables", representatives);
			report.put("coldMillis", coldMillis);
			Path out = config.getReportDir()
				.resolveSibling("schema-graph")
				.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-"
						+ config.getSchemaName());
			Files.createDirectories(out);
			mapper.writeValue(out.resolve("edges.json").toFile(), report);
			log.info("S4-1 graph check: {} -> {}", report.get("summary"), out.toAbsolutePath());
			log.info("S4-2 join paths: {}", ((Map<?, ?>) report.get("joinPaths")).get("summary"));

			// 建图必须覆盖全部表；三类边里至少物理外键和命名推断要有（数据集里都有）
			assertEquals(env.getTableCount(), graph.getTables().size());
			assertTrue(graph.edgeCount(EdgeType.FOREIGN_KEY) > 0, "no foreign key edges");
			assertTrue(graph.edgeCount(EdgeType.NAMING_INFERENCE) > 0, "no naming inference edges");
			assertSame(graph, hit, "second call should return the cached graph");
		}
	}

	/**
	 * S4-2 路径补全在题集上的离线检查（不调用大模型）：
	 * <ul>
	 * <li>连通：每道题的标准答案表（≥ 2 张）作为终端，能否连成一棵树；</li>
	 * <li>留一法：标准答案连成的树里，依次拿掉一张<b>中间表</b>（树上度 ≥ 2），剩下的作为终端，看补全能否把它补回来（模拟"精选时漏掉的中间表"）；
	 * 补回的是另一张能起同样连接作用的表（例如派生层的明细宽表）单独计数；端点表拿掉后本来就不需要补，不计入；</li>
	 * <li>耗时 P50 / P95。</li>
	 * </ul>
	 */
	private static Map<String, Object> checkJoinPaths(SchemaGraph graph, List<BenchmarkQuestion> questions) {
		int multi = 0;
		int connected = 0;
		int leaveOneOut = 0;
		int recovered = 0;
		int alternative = 0;
		List<Long> micros = new ArrayList<>();
		List<String> notConnected = new ArrayList<>();
		List<String> notRecovered = new ArrayList<>();
		for (BenchmarkQuestion q : questions) {
			List<String> gold = q.getTables();
			if (gold.size() < 2) {
				continue;
			}
			multi++;
			long start = System.nanoTime();
			JoinPlan plan = JoinPathResolver.resolve(graph, gold, 5);
			micros.add((System.nanoTime() - start) / 1_000);
			boolean ok = plan.getComponents() == 1 && plan.getMissingTerminals().isEmpty()
					&& plan.getSkippedTerminals().isEmpty();
			if (ok) {
				connected++;
			}
			else {
				notConnected.add(q.getId() + " " + gold + " components=" + plan.getComponents() + " missing="
						+ plan.getMissingTerminals());
			}
			Map<String, Integer> degree = new HashMap<>();
			for (SchemaEdge e : plan.getEdges()) {
				degree.merge(e.getFromTable(), 1, Integer::sum);
				degree.merge(e.getToTable(), 1, Integer::sum);
			}
			for (String removed : gold) {
				if (degree.getOrDefault(removed, 0) < 2) {
					continue;
				}
				List<String> terminals = new ArrayList<>(gold);
				terminals.remove(removed);
				JoinPlan partial = JoinPathResolver.resolve(graph, terminals, 5);
				leaveOneOut++;
				if (partial.getAddedTables().contains(removed)) {
					recovered++;
				}
				else if (partial.getComponents() == 1 && !partial.getAddedTables().isEmpty()) {
					alternative++;
					notRecovered.add(q.getId() + " removed " + removed + " -> alternative " + partial.getAddedTables());
				}
				else {
					notRecovered.add(q.getId() + " removed " + removed + " -> added " + partial.getAddedTables()
							+ " components=" + partial.getComponents());
				}
			}
		}
		Collections.sort(micros);
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("multiTableQuestions", multi);
		summary.put("connected", connected);
		summary.put("leaveOneOutCases", leaveOneOut);
		summary.put("recovered", recovered);
		summary.put("alternativeBridge", alternative);
		summary.put("p50Micros", micros.isEmpty() ? 0 : micros.get(micros.size() / 2));
		summary.put("p95Micros",
				micros.isEmpty() ? 0 : micros.get(Math.min(micros.size() - 1, (int) (micros.size() * 0.95))));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("summary", summary);
		result.put("notConnected", notConnected);
		result.put("notRecovered", notRecovered);
		return result;
	}

	/** 小写表名 → 主键列（小写）。 */
	private static Map<String, Set<String>> primaryKeys(BenchmarkConfig config) throws Exception {
		Map<String, Set<String>> keys = new HashMap<>();
		String sql = "SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE "
				+ "WHERE TABLE_SCHEMA = ? AND CONSTRAINT_NAME = 'PRIMARY'";
		try (Connection c = DriverManager.getConnection(config.getJdbcUrl(), config.getDbUser(),
				config.getDbPassword()); PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, config.getSchemaName());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					keys.computeIfAbsent(rs.getString(1).toLowerCase(), k -> new HashSet<>())
						.add(rs.getString(2).toLowerCase());
				}
			}
		}
		return keys;
	}

	/** 无向的列对键：a.x|b.y，两端按字典序排列。 */
	private static String pairKey(String t1, String c1, String t2, String c2) {
		String a = (t1 + "." + c1).toLowerCase();
		String b = (t2 + "." + c2).toLowerCase();
		return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
	}

	private static Map<String, Object> check(SchemaGraph graph, JsonNode clean, Map<String, Set<String>> pks) {
		Map<String, SchemaEdge> byPair = new HashMap<>();
		for (SchemaEdge e : graph.getEdges()) {
			byPair.put(pairKey(e.getFromTable(), e.getFromColumn(), e.getToTable(), e.getToColumn()), e);
		}

		// 1. 干净外键的召回
		List<Map<String, Object>> gold = new ArrayList<>();
		Set<String> goldKeys = new HashSet<>();
		int foundByFk = 0;
		int foundByNaming = 0;
		for (JsonNode fk : clean.get("foreign_keys")) {
			String key = pairKey(fk.get("table").asText(), fk.get("column").asText(), fk.get("ref_table").asText(),
					fk.get("ref_column").asText());
			goldKeys.add(key);
			SchemaEdge e = byPair.get(key);
			String foundAs = e == null ? "missing" : e.getType().name();
			foundByFk += e != null && e.getType() == EdgeType.FOREIGN_KEY ? 1 : 0;
			foundByNaming += e != null && e.getType() == EdgeType.NAMING_INFERENCE ? 1 : 0;
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("foreignKey", key);
			row.put("foundAs", foundAs);
			gold.add(row);
		}

		// 2. 命名推断边的合理性：至少一端是该表主键；核心表之间的、不在干净外键里的逐条列出
		Set<String> coreTables = new HashSet<>();
		clean.get("tables").fields().forEachRemaining(t -> {
			if ("core".equals(t.getValue().path("group").asText())) {
				coreTables.add(t.getKey().toLowerCase());
			}
		});
		int naming = 0;
		int namingToPk = 0;
		List<String> coreNotInGold = new ArrayList<>();
		Map<String, Integer> byColumn = new TreeMap<>();
		List<String> namingEdges = new ArrayList<>();
		for (SchemaEdge e : graph.getEdges()) {
			if (e.getType() != EdgeType.NAMING_INFERENCE) {
				continue;
			}
			naming++;
			boolean toPk = pks.getOrDefault(e.getToTable().toLowerCase(), Collections.emptySet())
				.contains(e.getToColumn().toLowerCase())
					|| pks.getOrDefault(e.getFromTable().toLowerCase(), Collections.emptySet())
						.contains(e.getFromColumn().toLowerCase());
			namingToPk += toPk ? 1 : 0;
			byColumn.merge(e.getFromColumn().toLowerCase(), 1, Integer::sum);
			String key = pairKey(e.getFromTable(), e.getFromColumn(), e.getToTable(), e.getToColumn());
			namingEdges.add(e.hint() + (toPk ? "" : "   [no primary key on either side]")
					+ String.format(" (%.2f)", e.getConfidence()));
			if (coreTables.contains(e.getFromTable().toLowerCase()) && coreTables.contains(e.getToTable().toLowerCase())
					&& !goldKeys.contains(key)) {
				coreNotInGold.add(e.hint() + (toPk ? "" : "   [no primary key on either side]"));
			}
		}

		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("goldForeignKeys", gold.size());
		summary.put("goldFoundAsForeignKey", foundByFk);
		summary.put("goldFoundByNaming", foundByNaming);
		summary.put("goldMissing", gold.size() - foundByFk - foundByNaming);
		summary.put("namingEdges", naming);
		summary.put("namingEdgesWithPrimaryKey", namingToPk);
		summary.put("coreNamingEdgesNotInGold", coreNotInGold.size());

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("summary", summary);
		report.put("gold", gold);
		report.put("coreNamingEdgesNotInGold", coreNotInGold);
		report.put("namingEdgesBySourceColumn", byColumn);
		report.put("namingEdges", namingEdges);
		return report;
	}

}
