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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.DynamicModelFactory;
import com.alibaba.cloud.ai.dataagent.service.schemalink.index.CachingEmbeddingModel;
import com.alibaba.cloud.ai.dataagent.service.schemalink.index.FileEmbeddingCacheStore;
import com.alibaba.cloud.ai.dataagent.util.ColumnTypeUtil;
import com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Embedding 预热：把被评测库的全部表文档、列文档文本提前向量化并写入 embedding 缓存，并在控制台显示进度条。
 * <p>
 * 之后跑 {@link SchemaRecallBenchmarkIT}、{@link EndToEndBenchmarkIT} 时，初始化阶段全部命中缓存。文本由生产代码
 * {@link DocumentConverterUtil} 生成，列类型经 {@link ColumnTypeUtil#wrapType} 转换，与 MySQL
 * 数据源初始化时得到的文本一致。
 * <p>
 * 只调用本地 Embedding 服务（默认 Ollama），不调用对话模型、不花 token。运行（在 DataAgent 根目录）：
 *
 * <pre>
 * .\mvnw.cmd -pl data-agent-management test "-Dtest=EmbeddingWarmupIT" "-Djacoco.skip=true" "-Dbenchmark.schema=enterprise_dw_l"
 * </pre>
 *
 * 可选：{@code -Dbenchmark.warmup.batch=32}（每次请求的文本数）。
 */
class EmbeddingWarmupIT {

	private static final int BAR_WIDTH = 40;

	/** 两次进度输出之间至少间隔的毫秒数（避免刷屏）。 */
	private static final long REPORT_INTERVAL_MILLIS = 5_000;

	@Test
	void warmUp() throws Exception {
		BenchmarkConfig config = BenchmarkConfig.from(System.getProperties(), System.getenv());
		int batch = Integer.parseInt(System.getProperty("benchmark.warmup.batch", "32"));

		System.out.println("[warmup] reading schema of " + config.displayJdbcUrl() + " ...");
		List<String> texts = schemaTexts(config);
		System.out.println("[warmup] " + texts.size() + " distinct texts, batch=" + batch + ", model="
				+ config.getEmbeddingModel() + " @ " + config.getEmbeddingBaseUrl());

		ModelConfigDTO modelConfig = new ModelConfigDTO();
		modelConfig.setProvider("custom");
		modelConfig.setBaseUrl(config.getEmbeddingBaseUrl());
		modelConfig.setModelName(config.getEmbeddingModel());
		EmbeddingModel remote = new DynamicModelFactory().createEmbeddingModel(modelConfig);
		CachingEmbeddingModel model = new CachingEmbeddingModel(remote, config.getEmbeddingModel(),
				FileEmbeddingCacheStore.forModel(config.getEmbeddingCacheDir(), config.getEmbeddingModel()));

		long start = System.currentTimeMillis();
		long lastReport = 0;
		int done = 0;
		for (int i = 0; i < texts.size(); i += batch) {
			List<String> chunk = texts.subList(i, Math.min(i + batch, texts.size()));
			List<float[]> vectors = model.embed(chunk);
			assertEquals(chunk.size(), vectors.size());
			done += chunk.size();
			long now = System.currentTimeMillis();
			if (now - lastReport >= REPORT_INTERVAL_MILLIS || done == texts.size()) {
				System.out.println(progressLine(done, texts.size(), model.getMissCount(), now - start));
				System.out.flush();
				lastReport = now;
			}
		}
		System.out.println("[warmup] done: " + texts.size() + " texts, " + model.getMissCount() + " newly embedded, "
				+ model.getHitCount() + " from cache, " + (System.currentTimeMillis() - start) / 1000 + " s");
	}

	/**
	 * 例：[##########..............................] 25.0% 6000/24104 新算 5800 24.8/s 剩余约
	 * 12:10
	 */
	static String progressLine(int done, int total, long misses, long elapsedMillis) {
		double ratio = total == 0 ? 1 : (double) done / total;
		int filled = (int) Math.round(ratio * BAR_WIDTH);
		StringBuilder bar = new StringBuilder("[");
		for (int i = 0; i < BAR_WIDTH; i++) {
			bar.append(i < filled ? '#' : '.');
		}
		bar.append(']');
		double perSecond = elapsedMillis <= 0 ? 0 : done * 1000.0 / elapsedMillis;
		long etaSeconds = perSecond <= 0 ? 0 : (long) ((total - done) / perSecond);
		return String.format("%s %5.1f%%  %d/%d  new %d  %.1f/s  ETA %02d:%02d", bar, ratio * 100, done, total, misses,
				perSecond, etaSeconds / 60, etaSeconds % 60);
	}

	/** 与生产初始化一致的表文档、列文档文本（去重，保持顺序）。 */
	private static List<String> schemaTexts(BenchmarkConfig config) throws Exception {
		Map<String, TableInfoBO> tables = new LinkedHashMap<>();
		try (Connection c = DriverManager.getConnection(config.getJdbcUrl(), config.getDbUser(),
				config.getDbPassword())) {
			try (PreparedStatement ps = c
				.prepareStatement("SELECT TABLE_NAME, TABLE_COMMENT FROM information_schema.TABLES "
						+ "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME")) {
				ps.setString(1, config.getSchemaName());
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						TableInfoBO table = new TableInfoBO();
						table.setName(rs.getString(1));
						table.setDescription(rs.getString(2));
						table.setColumns(new ArrayList<>());
						tables.put(table.getName(), table);
					}
				}
			}
			try (PreparedStatement ps = c.prepareStatement("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT "
					+ "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? ORDER BY TABLE_NAME, ORDINAL_POSITION")) {
				ps.setString(1, config.getSchemaName());
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						TableInfoBO table = tables.get(rs.getString(1));
						if (table != null) {
							table.getColumns()
								.add(ColumnInfoBO.builder()
									.name(rs.getString(2))
									.type(ColumnTypeUtil.wrapType(rs.getString(3)))
									.description(rs.getString(4))
									.build());
						}
					}
				}
			}
		}
		Set<String> texts = new LinkedHashSet<>();
		for (TableInfoBO table : tables.values()) {
			texts
				.add(DocumentConverterUtil.convertTableToDocument(BenchmarkEnvironment.DATASOURCE_ID, table).getText());
			for (ColumnInfoBO column : table.getColumns()) {
				texts.add(
						DocumentConverterUtil.convertColumnToDocument(BenchmarkEnvironment.DATASOURCE_ID, table, column)
							.getText());
			}
		}
		return new ArrayList<>(texts);
	}

}
