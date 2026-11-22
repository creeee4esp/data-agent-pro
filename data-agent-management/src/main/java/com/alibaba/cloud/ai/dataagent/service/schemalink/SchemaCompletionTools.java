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

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 补全阶段提供给大模型的工具。每次召回新建一个实例：记录本次已召回过的列和工具调用次数。
 * <p>
 * explore_schema、verify_schema 会在业务库上执行大模型写的 SQL：只允许单条 SELECT（Druid 解析校验），执行器自带最多 1000
 * 行、30 秒超时； 部署时应给数据源配置只读账号。
 */
final class SchemaCompletionTools {

	/**
	 * 每个工具在 prompt"工作方式"里的说明，按展示顺序排列；只有启用的工具才出现在 prompt 里。
	 */
	static final Map<String, String> TOOL_GUIDES;

	/** 每个工具被调用的次数（含超出上限、未执行的调用），按首次调用的顺序排列，用于日志和诊断。 */
	private final Map<String, Integer> callsByTool = new LinkedHashMap<>();

	static {
		Map<String, String> guides = new LinkedHashMap<>();
		guides.put("retrieve_schema", "用你自己写的检索词查找相关的列，例如\"商品品类名称\"");
		guides.put("describe_tables", "查看指定表的全部列，用于确认 JOIN 路径和字段含义");
		guides.put("expand_family", "查看被折叠的分表、备份、副本，仅当问题明确提到某张分表、备份或某个时间分片时使用");
		guides.put("explore_schema", "执行一条只读 SELECT 查看样本或取值（例如某个状态、类型、品类在库里怎么存），用于确认列的含义");
		guides.put("verify_schema", "用你认为足够的表写一条回答问题的 SELECT 试执行，报错或结果明显不合理时说明还缺表或用错了列");
		TOOL_GUIDES = Collections.unmodifiableMap(guides);
	}

	/** 工具调用次数上限，超过后工具不再执行，只提示大模型直接输出结果。 */
	static final int MAX_CALLS = 5;

	/** retrieve_schema 每次返回的列数（定向检索，参考 AutoLink 的 3～5）。 */
	static final int RETRIEVE_TOP_K = 5;

	/** describe_tables 每次最多查看的表数。 */
	static final int MAX_DESCRIBE_TABLES = 5;

	/** expand_family 成员过多时，首尾各展示的个数。 */
	static final int FAMILY_PREVIEW = 10;

	/** expand_family 最多读取的同族表数。 */
	static final int MAX_FAMILY_MEMBERS = 10_000;

	/** explore_schema 返回的行数（参考 AutoLink：最多 5 行）。 */
	static final int EXPLORE_ROWS = 5;

	/** verify_schema 返回的行数：只用来确认结果是否合理。 */
	static final int VERIFY_ROWS = 3;

	static final String BUDGET_EXHAUSTED = "已达到工具调用上限，请根据已有信息直接输出结果。";

	private final Integer datasourceId;

	private final SchemaService schemaService;

	private final AgentVectorStoreService vectorStoreService;

	/** 在业务库上执行 SQL；为 {@code null} 时两个 SQL 工具不可用。 */
	private final Function<String, ResultSetBO> sqlRunner;

	/** 数据库方言，用于解析 SQL（如 mysql）。 */
	private final String dialect;

	/** 已召回过的列，键为 表名.列名。 */
	private final Set<String> seenColumns = new HashSet<>();

	private int calls;

	SchemaCompletionTools(Integer datasourceId, SchemaService schemaService, AgentVectorStoreService vectorStoreService,
			List<Document> warmColumns) {
		this(datasourceId, schemaService, vectorStoreService, warmColumns, null, null);
	}

	SchemaCompletionTools(Integer datasourceId, SchemaService schemaService, AgentVectorStoreService vectorStoreService,
			List<Document> warmColumns, Function<String, ResultSetBO> sqlRunner, String dialect) {
		this.datasourceId = datasourceId;
		this.schemaService = schemaService;
		this.vectorStoreService = vectorStoreService;
		this.sqlRunner = sqlRunner;
		this.dialect = dialect;
		for (Document column : warmColumns) {
			seenColumns.add(columnKey(column));
		}
	}

	@Tool(name = "retrieve_schema", description = "用检索词查找相关的列（排除已经召回过的列），返回列及其所属表")
	public String retrieveSchema(@ToolParam(description = "检索词，描述要找的列，例如：商品品类名称") String query) {
		if (overBudget("retrieve_schema")) {
			return BUDGET_EXHAUSTED;
		}
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Document> hits = vectorStoreService.similaritySearch(query,
				b.and(b.and(b.eq(Constant.DATASOURCE_ID, datasourceId.toString()),
						b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN)),
						b.eq(DocumentMetadataConstant.FAMILY_REPRESENTATIVE, true))
					.build(),
				RETRIEVE_TOP_K + seenColumns.size(), 0.0);
		List<Document> fresh = new ArrayList<>();
		for (Document hit : hits) {
			if (fresh.size() < RETRIEVE_TOP_K && seenColumns.add(columnKey(hit))) {
				fresh.add(hit);
			}
		}
		return fresh.isEmpty() ? "没有新的相关列。" : formatColumnsByTable(fresh);
	}

	@Tool(name = "describe_tables", description = "查看指定表的全部列（名称、类型、注释）")
	public String describeTables(@ToolParam(description = "表名列表，一次最多 5 张") List<String> tableNames) {
		if (overBudget("describe_tables")) {
			return BUDGET_EXHAUSTED;
		}
		List<String> names = tableNames.size() > MAX_DESCRIBE_TABLES ? tableNames.subList(0, MAX_DESCRIBE_TABLES)
				: tableNames;
		List<Document> columns = schemaService.getColumnDocumentsByTableName(datasourceId, names);
		for (Document column : columns) {
			seenColumns.add(columnKey(column));
		}
		return columns.isEmpty() ? "没有找到这些表：" + names : formatColumnsByTable(columns);

	}

	@Tool(name = "expand_family", description = "查看一张代表表被折叠的同族表（分表、备份、副本）")
	public String expandFamily(@ToolParam(description = "代表表的表名") String familyId) {
		if (overBudget("expand_family")) {
			return BUDGET_EXHAUSTED;
		}
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Document> tables = vectorStoreService.getDocumentsOnlyByFilter(b.and(
				b.and(b.eq(Constant.DATASOURCE_ID, datasourceId.toString()),
						b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE)),
				b.eq(DocumentMetadataConstant.FAMILY_ID, familyId))
			.build(), MAX_FAMILY_MEMBERS);
		List<String> members = new ArrayList<>();
		for (Document table : tables) {
			String name = String.valueOf(table.getMetadata().get(DocumentMetadataConstant.NAME));
			if (!name.equals(familyId)) {
				members.add(name);
			}
		}
		if (members.isEmpty()) {
			return familyId + "没有同族表。";
		}
		members.sort(null);
		if (members.size() > 2 * FAMILY_PREVIEW) {
			List<String> preview = new ArrayList<>(members.subList(0, FAMILY_PREVIEW));
			preview.add("...");
			preview.addAll(members.subList(members.size() - FAMILY_PREVIEW, members.size()));
			members = preview;
		}
		return familyId + " 的同族表（共 " + (tables.size() - 1) + " 张）：" + String.join(", ", members);
	}

	@Tool(name = "explore_schema",
			description = "在数据库上执行一条只读 SELECT，查看样本数据或取值（例如 SELECT DISTINCT 某列），最多返回 5 行。用于确认列的含义和取值，不要用它回答问题")
	public String exploreSchema(@ToolParam(description = "一条 SELECT 语句") String sql) {
		if (overBudget("explore_schema")) {
			return BUDGET_EXHAUSTED;
		}
		return runReadOnly(sql, EXPLORE_ROWS);
	}

	@Tool(name = "verify_schema", description = "试执行一条能回答问题的完整 SELECT，检查所选的表和列是否足够：成功返回行数和前 3 行，失败返回数据库的报错（例如列不存在）")
	public String verifySchema(@ToolParam(description = "回答问题的完整 SELECT 语句") String sql) {
		if (overBudget("verify_schema")) {
			return BUDGET_EXHAUSTED;
		}
		return runReadOnly(sql, VERIFY_ROWS);
	}

	/** 校验只读后执行，返回总行数（执行器最多取 1000 行）和前 {@code previewRows} 行；出错时返回报错信息。 */
	private String runReadOnly(String sql, int previewRows) {
		if (sqlRunner == null) {
			return "当前环境不支持执行 SQL。";
		}
		String rejection = readOnlyViolation(sql, dialect);
		if (rejection != null) {
			return rejection;
		}
		try {
			ResultSetBO result = sqlRunner.apply(sql);
			if (result == null) {
				return "执行失败：没有返回结果。";
			}
			if (result.getErrorMsg() != null && !result.getErrorMsg().isEmpty()) {
				return "执行失败：" + result.getErrorMsg();
			}
			return formatRows(result, previewRows);
		}
		catch (RuntimeException e) {
			return "执行失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
	}

	/** 只允许单条 SELECT；返回拒绝原因，通过时返回 {@code null}。 */
	static String readOnlyViolation(String sql, String dialect) {
		List<SQLStatement> statements;
		try {
			DbType dbType = dialect == null ? null : DbType.of(dialect.toLowerCase());
			statements = SQLUtils.parseStatements(sql, dbType == null ? DbType.other : dbType);
		}
		catch (RuntimeException e) {
			return "SQL 无法解析：" + e.getMessage();
		}
		if (statements.size() != 1 || !(statements.get(0) instanceof SQLSelectStatement)) {
			return "只允许执行一条 SELECT 语句。";
		}
		return null;
	}

	static String formatRows(ResultSetBO result, int previewRows) {
		List<Map<String, String>> rows = result.getData() == null ? new ArrayList<>() : result.getData();
		List<String> columns = result.getColumn() == null ? new ArrayList<>() : result.getColumn();
		if (rows.isEmpty()) {
			return "执行成功，结果为空。";
		}
		StringBuilder sb = new StringBuilder("执行成功，共 ").append(rows.size())
			.append(" 行，前 ")
			.append(Math.min(previewRows, rows.size()))
			.append(" 行：\n")
			.append(String.join(" | ", columns))
			.append('\n');
		for (int i = 0; i < Math.min(previewRows, rows.size()); i++) {
			List<String> values = new ArrayList<>();
			for (String column : columns) {
				values.add(String.valueOf(rows.get(i).get(column)));
			}
			sb.append(String.join(" | ", values)).append('\n');
		}
		return sb.toString();
	}

	/**
	 * 按 {@link #TOOL_GUIDES} 的顺序，生成已启用工具的说明（每行一个，最后一行以句号结尾）。
	 */
	static String toolGuide(List<String> enabled) {
		List<String> lines = new ArrayList<>();
		for (Map.Entry<String, String> e : TOOL_GUIDES.entrySet()) {
			if (enabled.contains(e.getKey())) {
				lines.add("   - " + e.getKey() + "：" + e.getValue());
			}
		}
		return lines.isEmpty() ? "" : String.join("；\n", lines) + "。";
	}

	/** 记一次调用；返回是否已超出上限（超出时工具不再执行）。 */
	private boolean overBudget(String tool) {
		callsByTool.merge(tool, 1, Integer::sum);
		return ++calls > MAX_CALLS;
	}

	Map<String, Integer> getCallsByTool() {
		return Collections.unmodifiableMap(callsByTool);
	}

	int getCalls() {
		return calls;
	}

	/** 按表分组输出列: 每张表一段，每列一行"列名 类型 注释" */
	static String formatColumnsByTable(List<Document> columns) {
		Map<String, List<String>> byTable = new LinkedHashMap<>();
		for (Document column : columns) {
			Map<String, Object> m = column.getMetadata();
			String line = m.get(DocumentMetadataConstant.NAME) + " " + m.get("type")
					+ (Boolean.TRUE.equals(m.get("primary")) ? " 主键" : "") + " " + m.getOrDefault("description", "");
			byTable.computeIfAbsent(String.valueOf(m.get(DocumentMetadataConstant.TABLE_NAME)), k -> new ArrayList<>())
				.add("  " + line.trim());
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, List<String>> e : byTable.entrySet()) {
			sb.append("# ").append(e.getKey()).append('\n');
			for (String line : e.getValue()) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	private static String columnKey(Document column) {
		Map<String, Object> m = column.getMetadata();
		return m.get(DocumentMetadataConstant.TABLE_NAME) + "." + m.get(DocumentMetadataConstant.NAME);
	}

}
