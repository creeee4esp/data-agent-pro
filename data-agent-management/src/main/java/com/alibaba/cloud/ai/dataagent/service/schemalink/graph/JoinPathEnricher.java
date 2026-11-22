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
package com.alibaba.cloud.ai.dataagent.service.schemalink.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 外键多跳补全（S4-3）：精选表之后，把选中的表当终端，在表关系图上找连通路径（{@link JoinPathResolver}）， 把路径经过的中间表补进
 * {@link SchemaDTO}，并把路径上的每一段写成 JOIN 提示。
 * <p>
 * 开关 {@code schema-linking.join-graph.enabled}，默认关闭。任何异常都只记日志、原样返回，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JoinPathEnricher {

	private final SchemaGraphCache graphCache;

	private final SchemaService schemaService;

	private final DataAgentProperties properties;

	/**
	 * @param datasourceId 当前数据源（向量库里 schema 文档的 datasourceId）
	 * @param schema 精选后的 schema，会被原地修改：补中间表、写 joinHints
	 * @return 补全结果；未开启、终端不足 2 张或出错时为 {@code null}
	 */
	public JoinPlan enrich(Integer datasourceId, SchemaDTO schema) {
		DataAgentProperties.SchemaLinking.JoinGraph config = properties.getSchemaLinking().getJoinGraph();
		if (!config.isEnabled() || datasourceId == null || schema == null || schema.getTable() == null
				|| schema.getTable().size() < 2) {
			return null;
		}
		List<String> terminals = new ArrayList<>();
		for (TableDTO table : schema.getTable()) {
			terminals.add(table.getName());
		}
		try {
			JoinPlan plan = JoinPathResolver.resolve(graphCache.get(datasourceId), terminals,
					config.getMaxAddedTables());
			addTables(datasourceId, schema, plan.getAddedTables());
			List<String> hints = new ArrayList<>();
			for (SchemaEdge edge : plan.getEdges()) {
				hints.add(edge.hint());
			}
			schema.setJoinHints(hints);
			log.info(
					"Join path enrichment: datasource={}, terminals={}, added={}, hints={}, components={}, "
							+ "missing={}, skipped={}",
					datasourceId, plan.getTerminals(), plan.getAddedTables(), hints.size(), plan.getComponents(),
					plan.getMissingTerminals(), plan.getSkippedTerminals());
			return plan;
		}
		catch (RuntimeException e) {
			log.warn("Join path enrichment failed for datasource {}; keeping selected schema as is", datasourceId, e);
			return null;
		}
	}

	/** 按表名从向量库取表文档和列文档，组装成 TableDTO 追加到 schema（只追加补全给出的表）。 */
	private void addTables(Integer datasourceId, SchemaDTO schema, List<String> tableNames) {
		if (tableNames.isEmpty()) {
			return;
		}
		SchemaDTO extra = new SchemaDTO();
		schemaService.buildSchemaFromDocuments(null,
				schemaService.getColumnDocumentsByTableName(datasourceId, tableNames),
				schemaService.getTableDocuments(datasourceId, tableNames), extra);
		Set<String> wanted = new HashSet<>();
		for (String name : tableNames) {
			wanted.add(name.toLowerCase(Locale.ROOT));
		}
		Set<String> present = new HashSet<>();
		for (TableDTO table : schema.getTable()) {
			present.add(table.getName().toLowerCase(Locale.ROOT));
		}
		// buildSchemaFromDocuments 还会按外键再补 1 跳，这里只要补全给出的表
		for (TableDTO table : extra.getTable()) {
			String name = table.getName().toLowerCase(Locale.ROOT);
			if (wanted.contains(name) && present.add(name)) {
				schema.getTable().add(table);
			}
		}
	}

}
