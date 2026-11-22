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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.prompt.PromptConstant;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 补全阶段：把热启动的结果交给大模型，由它调用工具补充缺少的表，最后输出所需的表名。
 * <p>
 * 工具调用的循环由 Spring AI 的 ChatClient 完成；本类只负责组装 prompt、提供工具和校验输出。 大模型调用失败或输出无法解析时，退回热启动的结果。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaCompletionAgent {

	/** 读取数据源全部表文档的上限（用于家族清单和表名校验）。 */
	static final int MAX_TABLES_IN_DATASOURCE = 10_000;

	private static final Pattern TABLE_JSON = Pattern.compile("\\{[^{}]*\"tables\"\\s*:\\s*\\[[^\\]]*\\][^{}]*\\}");

	private final AiModelRegistry aiModelRegistry;

	private final SchemaService schemaService;

	private final AgentVectorStoreService vectorStoreService;

	/** 取召回请求所属智能体的数据源，供 explore_schema / verify_schema 执行 SQL。 */
	private final DatabaseUtil databaseUtil;

	/** 读取启用的工具（schema-linking.completion-tools）。 */
	private final DataAgentProperties properties;

	/**
	 * @param request 召回请求（问题、业务口径）
	 * @param warmColumns 热启动命中的列，按相关度排序
	 * @param warmTables 热启动排出的表
	 * @return 大模型确认的表名；失败时返回 {@code warmTables}
	 */
	public List<String> complete(SchemaLinkRequest request, List<Document> warmColumns, List<String> warmTables) {
		Integer datasourceId = request.getDatasourceId();
		List<Document> allTables = allTableDocuments(datasourceId);
		SchemaCompletionTools tools = newTools(request, warmColumns);

		Map<String, Object> params = new HashMap<>();
		params.put("question", request.getCanonicalQuery());
		params.put("evidence", StringUtils.hasText(request.getEvidence()) ? request.getEvidence() : "无");
		params.put("families", formatFamilies(allTables));
		params.put("schema", SchemaCompletionTools.formatColumnsByTable(warmColumns));
		List<String> enabled = properties.getSchemaLinking().getCompletionTools();
		params.put("tools", SchemaCompletionTools.toolGuide(enabled));
		String prompt = PromptConstant.getSchemaCompletionPromptTemplate().render(params);

		try {
			// 这一次 call() 内部是多轮的"调工具 → 看结果 → 再决定"循环，由 Spring AI 完成，不在本项目代码里：
			// OpenAiChatModel#internalCall 请求模型后，若回复里有 tool_calls，就用 ToolCallingManager
			// 执行对应的
			// @Tool 方法（SchemaCompletionTools），把"原对话 + 工具调用 + 工具结果"作为新 prompt 递归调用自己；
			// 模型不再调用工具、直接给出文本时递归结束，content() 拿到的就是最终回答。
			// 因此：每轮都会重发完整对话（token 随轮次累加）；框架不限制轮数，上限由工具内部计数（MAX_CALLS）控制；
			// 评测的 CachingChatModel 包在外面，缓存的是整个循环的最终结果。
			// 若需要逐轮记录或自己控制停止，可设置 internalToolExecutionEnabled(false) 改为手写循环。
			String output = aiModelRegistry.getChatClient()
				.prompt()
				.user(prompt)
				.toolCallbacks(enabledCallbacks(tools, enabled))
				.call()
				.content();
			List<String> tables = parseTables(output, tableNames(allTables));
			log.info("Schema completion: datasource={}, toolCalls={} {}, tables={}", datasourceId, tools.getCalls(),
					tools.getCallsByTool(), tables);
			if (tables.isEmpty()) {
				log.warn("Schema completion returned no valid table, falling back to warm start. Output: {}", output);
				return warmTables;
			}
			return tables;
		}
		catch (RuntimeException e) {
			log.warn("Schema completion failed, falling back to warm start", e);
			return warmTables;
		}

	}

	/** 只把启用的工具交给大模型；prompt 里也只描述这些工具（见 {@link SchemaCompletionTools#toolGuide}）。 */
	static List<ToolCallback> enabledCallbacks(SchemaCompletionTools tools, List<String> enabled) {
		List<ToolCallback> callbacks = new ArrayList<>();
		for (ToolCallback callback : ToolCallbacks.from(tools)) {
			if (enabled.contains(callback.getToolDefinition().name())) {
				callbacks.add(callback);
			}
		}
		return callbacks;
	}

	/** 业务库连接取不到时，两个 SQL 工具不可用，其余工具照常。 */
	private SchemaCompletionTools newTools(SchemaLinkRequest request, List<Document> warmColumns) {
		Integer datasourceId = request.getDatasourceId();
		try {
			Long agentId = StringUtils.hasText(request.getAgentId()) ? Long.valueOf(request.getAgentId()) : null;
			DbConfigBO dbConfig = databaseUtil.getAgentDbConfig(agentId);
			Accessor accessor = databaseUtil.getAgentAccessor(agentId);
			Function<String, ResultSetBO> runner = sql -> {
				try {
					return accessor.executeSqlAndReturnObject(dbConfig,
							DbQueryParameter.from(dbConfig).setSchema(dbConfig.getSchema()).setSql(sql));
				}
				catch (Exception e) {
					throw new IllegalStateException(e.getMessage(), e);
				}
			};
			return new SchemaCompletionTools(datasourceId, schemaService, vectorStoreService, warmColumns, runner,
					dbConfig.getDialectType());
		}
		catch (RuntimeException e) {
			log.warn("Cannot resolve datasource for SQL tools, explore/verify disabled: {}", e.getMessage());
			return new SchemaCompletionTools(datasourceId, schemaService, vectorStoreService, warmColumns);
		}
	}

	/**
	 * 从大模型输出中取最后一个 {@code {"tables": [...]}}，只保留真实存在的表名（大小写不敏感，返回真实表名）。
	 */
	static List<String> parseTables(String output, Set<String> existing) {
		List<String> result = new ArrayList<>();
		if (output == null)
			return result;
		String json = null;
		Matcher m = TABLE_JSON.matcher(output);
		while (m.find()) {
			json = m.group();
		}
		if (json == null)
			return result;
		Map<String, String> byLowerCase = new HashMap<>();
		for (String name : existing) {
			byLowerCase.put(name.toLowerCase(), name);
		}
		Set<String> tables = new LinkedHashSet<>();
		try {
			for (JsonNode node : JsonUtil.getObjectMapper().readTree(json).path("tables")) {
				String name = byLowerCase.get(node.asText().trim().toLowerCase());
				if (name != null) {
					tables.add(name);
				}
			}
		}
		catch (Exception e) {
			log.warn("Cannot parse schema completion output: {}", json, e);
		}
		result.addAll(tables);
		return result;
	}

	/** 家族清单：每个家族一行"代表表名 表注释"，有同族表时注明数量。 */
	static String formatFamilies(List<Document> allTables) {
		Map<String, Integer> members = new HashMap<>();
		Map<String, String> representatives = new TreeMap<>();
		for (Document table : allTables) {
			Map<String, Object> m = table.getMetadata();
			String name = String.valueOf(m.get(DocumentMetadataConstant.NAME));
			String familyId = String.valueOf(m.getOrDefault(DocumentMetadataConstant.FAMILY_ID, name));
			if (familyId.equals(name)) {
				representatives.put(name, String.valueOf(m.getOrDefault("description", "")));
			}
			else {
				members.merge(familyId, 1, Integer::sum);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> e : representatives.entrySet()) {
			sb.append(e.getKey());
			if (StringUtils.hasText(e.getValue())) {
				sb.append(' ').append(e.getValue());
			}
			Integer count = members.get(e.getKey());
			if (count != null) {
				sb.append("（另有 ").append(count).append(" 张分表/备份/副本）");
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	private List<Document> allTableDocuments(Integer datasourceId) {
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		return vectorStoreService
			.getDocumentsOnlyByFilter(
					b.and(b.eq(Constant.DATASOURCE_ID, datasourceId.toString()),
							b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE))
						.build(),
					MAX_TABLES_IN_DATASOURCE);
	}

	private static Set<String> tableNames(List<Document> tables) {
		Set<String> names = new LinkedHashSet<>();
		for (Document table : tables) {
			names.add(String.valueOf(table.getMetadata().get(DocumentMetadataConstant.NAME)));
		}
		return names;
	}

}
