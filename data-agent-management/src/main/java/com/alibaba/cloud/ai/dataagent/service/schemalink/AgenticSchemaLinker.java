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
package com.alibaba.cloud.ai.dataagent.service.schemalink;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agentic schema linking: 先"热启动"，再由大模型补全
 * <ol>
 * <li>用问题检索列向量 top-{@value #COLUMN_TOP_K}，只检索家族代表表的列；</li>
 * <li>按所属表聚合，按首次出现的顺序排名（检索结果已按分数从高到低排列，所以表的得分就是它命中列里的最高分）；</li>
 * <li>取前 {@value #MAX_TABLES} 张表，返回表文档和这些表的全部列文档（与 legacy 相同，下游不用改）。</li>
 * </ol>
 * T3 在此基础上加入大模型补全。{@value SchemaLinkingMode#PROPERTY} 配置为 {@value #NAME} 时注册。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = SchemaLinkingMode.PROPERTY, havingValue = AgenticSchemaLinker.NAME)
public class AgenticSchemaLinker implements SchemaLinker {

	public static final String NAME = "agentic";

	/** 列检索的候选数（参考 AutoLink 的初始检索规模 50～100）。 */
	static final int COLUMN_TOP_K = 50;

	/** 热启动最多返回的表数。 */
	static final int MAX_TABLES = 20;

	private final SchemaService schemaService;

	private final AgentVectorStoreService vectorStoreService;

	/** 补全阶段；为 {@code null} 时只做热启动（评测器用它做消融）。 */
	private final SchemaCompletionAgent completionAgent;

	@Override
	public LinkedSchema link(SchemaLinkRequest request) {
		long start = System.nanoTime();
		Integer datasourceId = request.getDatasourceId();

		List<Document> columns = vectorStoreService.similaritySearch(request.getCanonicalQuery(),
				representativeColumnFilter(datasourceId), COLUMN_TOP_K, 0.0);
		List<String> warmTables = rankTables(columns);
		List<String> tableNames = completionAgent == null || warmTables.isEmpty() ? warmTables
				: merge(completionAgent.complete(request, columns, warmTables), warmTables);

		List<Document> tableDocuments = orderByName(schemaService.getTableDocuments(datasourceId, tableNames),
				tableNames);

		List<Document> columnDocuments = schemaService.getColumnDocumentsByTableName(datasourceId, tableNames);

		long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
		log.debug("Agentic schema linking (warm start): datasource={}, tables={}, elapsed={}ms", datasourceId,
				tableNames, elapsedMillis);
		return new LinkedSchema(tableDocuments, columnDocuments, new LinkDiagnostics(NAME, elapsedMillis));
	}

	/** 本数据源、列文档、家族代表表。 */
	private static Filter.Expression representativeColumnFilter(Integer datasourceId) {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		return b.and(
				b.and(b.eq(Constant.DATASOURCE_ID, datasourceId.toString()),
						b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN)),
				b.eq(DocumentMetadataConstant.FAMILY_REPRESENTATIVE, true))
			.build();
	}

	/** 按列的检索顺序，取所属表的首次出现顺序，最多 {@value #MAX_TABLES} 张。 */
	static List<String> rankTables(List<Document> columns) {
		Set<String> ranked = new LinkedHashSet<>();
		for (Document column : columns) {
			ranked.add(String.valueOf(column.getMetadata().get(DocumentMetadataConstant.TABLE_NAME)));
			if (ranked.size() == MAX_TABLES) {
				break;
			}
		}
		return new ArrayList<>(ranked);
	}

	/** 按 {@code names} 的顺序排列表文档（按名字过滤查询不保证顺序）。 */
	private static List<Document> orderByName(List<Document> documents, List<String> names) {
		Map<String, Document> byName = new HashMap<>();
		for (Document doc : documents) {
			byName.put(String.valueOf(doc.getMetadata().get(DocumentMetadataConstant.NAME)), doc);
		}
		List<Document> ordered = new ArrayList<>();
		for (String name : names) {
			Document doc = byName.get(name);
			if (doc != null) {
				ordered.add(doc);
			}
		}
		return ordered;
	}

	/**
	 * 只增不善(与AutoLink一致): 大模型选出的表排在最前，其后接热启动的候选，去重后最多{@value #MAX_TABLES} 张
	 * 大模型偏好能直接作答的宽表、汇总表，若让它输出最终集合，会删掉热启动找到的正表；最终的精选交给下游TableRalationNode
	 */
	static List<String> merge(List<String> completed, List<String> warmTables) {
		Set<String> merged = new LinkedHashSet<>(completed);
		merged.addAll(warmTables);
		List<String> result = new ArrayList<>(merged);
		return result.size() > MAX_TABLES ? new ArrayList<>(result.subList(0, MAX_TABLES)) : result;
	}

}
