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
package com.alibaba.cloud.ai.dataagent.service.schemalink.graph;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 从表文档、列文档、逻辑关系建表关系图。
 *
 * <p>
 * 纯函数，不依赖 Spring 容器；同样的输入一定得到同样的图。
 */
public final class SchemaGraphBuilder {

	private static final String FOREIGN_KEY = "foreignKey";

	private static final String PRIMARY = "primary";

	private static final String TYPE = "type";

	private static final String FOREIGN_KEY_SEPARATOR = "、";

	private static final String ID_SUFFIX = "_id";

	private static final String KEY_SUFFIX = "_key";

	private static final String ID_COLUMN = "id";

	private static final double FOREIGN_KEY_CONFIDENCE = 1.0;

	private static final double LOGICAL_RELATION_CONFIDENCE = 0.9;

	private static final double NAMING_CONFIDENCE_BASE = 0.5;

	private static final double NAMING_CONFIDENCE_BONUS = 0.1;

	private static final double NAMING_CONFIDENCE_MAX = 0.7;

	private SchemaGraphBuilder() {
	}

	/**
	 * 建图的唯一入口。顺序很重要：先有点，才能判断边的两端是否存在；先加可信度高的边， 后面的命名推断才不会把物理外键覆盖掉。
	 * @param tableDocuments 表文档（metadata 里有 name、familyId、foreignKey）
	 * @param columnDocuments 列文档（metadata 里有 tableName、name、type、primary）
	 * @param logicalRelations 数据源上人工配置的逻辑关系
	 * @param options 命名推断的开关和候选数上限
	 * @return 只读图；同样输入一定得到同样的结果
	 */
	public static SchemaGraph build(List<Document> tableDocuments, List<Document> columnDocuments,
			List<LogicalRelation> logicalRelations, DataAgentProperties.SchemaLinking.JoinGraph options) {
		// 第一步：表文档 → 点。没有表就没有边，后面的步骤都依赖这个点表做端点校验
		Map<String, TableNode> tables = buildTables(tableDocuments);
		// 第二步：列文档 → 反向索引。命名推断要靠它回答“哪些表有 user_id”“哪张表的 user_id 是主键”
		ColumnIndex columnIndex = buildColumnIndex(tables, columnDocuments);

		// 第三步：按可信度从高到低加边。LinkedHashMap 只是为了插入顺序稳定，最终排序在 SchemaGraph 里做
		Map<String, SchemaEdge> edges = new LinkedHashMap<>();
		// 物理外键：可信度 1.0，先加
		int skippedEdges = addForeignKeyEdges(tables, tableDocuments, edges);
		// 逻辑关系：可信度 0.9，补充没建外键的库
		skippedEdges += addLogicalRelationEdges(tables, logicalRelations, edges);
		// 命名推断：可信度最低，最后加；候选数配成 0 就整段跳过
		if (options.getMaxNamingCandidates() > 0) {
			addNamingInferenceEdges(tables, columnIndex, options.getMaxNamingCandidates(), edges);
		}
		// 第四步：把点、边、丢弃计数交给 SchemaGraph 做排序和邻接表
		return new SchemaGraph(new ArrayList<>(tables.values()), new ArrayList<>(edges.values()), skippedEdges);
	}

	private static Map<String, TableNode> buildTables(List<Document> tableDocuments) {
		// TreeMap：表名有序，后续遍历点的顺序固定
		Map<String, TableNode> tables = new TreeMap<>();
		// 有任何文档带 familyId，说明索引已按表家族建过；此时缺 familyId 的是混进来的旧文档或漏写，宁可当非代表表（少推断边）也不要让分表、
		// 备份表当上代表表。所有文档都没有 familyId（家族功能之前建的索引）时，全部按代表表处理
		boolean familyIndexed = false;
		for (Document document : tableDocuments) {
			familyIndexed |= document.getMetadata().get(DocumentMetadataConstant.FAMILY_ID) != null;
		}
		for (Document document : tableDocuments) {
			// 表名是图的唯一标识；没有表名的文档没法建点，直接跳过
			String name = normalize(stringValue(document.getMetadata().get(DocumentMetadataConstant.NAME)));
			if (name == null) {
				continue;
			}
			// familyId 是家族代表表的表名：等于自己 → 代表表；缺失时见上面 familyIndexed 的说明
			// 派生表（user_info_daily 等）的 familyId 指向家族代表表，因此 representative=false
			Object familyId = document.getMetadata().get(DocumentMetadataConstant.FAMILY_ID);
			boolean representative = familyId == null ? !familyIndexed : name.equals(String.valueOf(familyId));
			tables.put(name, new TableNode(name, representative));
		}
		return tables;
	}

	private static ColumnIndex buildColumnIndex(Map<String, TableNode> tables, List<Document> columnDocuments) {
		ColumnIndex index = new ColumnIndex();
		// 1. 小写表名 → 真实表名。命名推断里表名匹配不区分大小写，但边必须写真实表名
		for (String tableName : tables.keySet()) {
			index.tableNamesByLowerName.put(tableName.toLowerCase(Locale.ROOT), tableName);
		}
		// 2. 逐列建立三张反向索引
		for (Document document : columnDocuments) {
			Map<String, Object> metadata = document.getMetadata();
			String tableName = normalize(stringValue(metadata.get(DocumentMetadataConstant.TABLE_NAME)));
			String columnName = normalize(stringValue(metadata.get(DocumentMetadataConstant.NAME)));
			// 只收图里存在的表；列名或表名缺失的文档直接跳过
			if (tableName == null || columnName == null || !tables.containsKey(tableName)) {
				continue;
			}
			// 列名统一用小写做键：user_id、USER_ID 视为同一列
			String lowerColumn = columnName.toLowerCase(Locale.ROOT);
			boolean primary = Boolean.parseBoolean(String.valueOf(metadata.get(PRIMARY)));
			ColumnInfo column = new ColumnInfo(columnName, normalize(stringValue(metadata.get(TYPE))));

			// 2.1 按表查列：表名 → (小写列名 → 列信息)
			index.columnsByTable.computeIfAbsent(tableName, key -> new TreeMap<>()).putIfAbsent(lowerColumn, column);
			// 2.2 按列名反查表：user_id → {trade_order, user_info, ...}
			index.tablesByColumn.computeIfAbsent(lowerColumn, key -> new TreeSet<>()).add(tableName);
			// 2.3 按主键列名反查表：user_id 是主键的那些表
			if (primary) {
				index.tablesByPrimaryColumn.computeIfAbsent(lowerColumn, key -> new TreeSet<>()).add(tableName);

				index.primaryColumnsByTable.computeIfAbsent(tableName, key -> new TreeSet<>()).add(lowerColumn);
			}
		}
		return index;
	}

	/**
	 * 解析表文档上的物理外键。生产代码在两张表上各写一遍同一条外键，靠边的去重键合并。
	 */
	private static int addForeignKeyEdges(Map<String, TableNode> tables, List<Document> tableDocuments,
			Map<String, SchemaEdge> edges) {
		// 同一张表可能出现多条表文档（理论上不会），按表名收一份，顺便让遍历顺序固定
		Map<String, Document> documentsByName = new TreeMap<>();
		for (Document document : tableDocuments) {
			String name = normalize(stringValue(document.getMetadata().get(DocumentMetadataConstant.NAME)));
			if (name != null) {
				documentsByName.put(name, document);
			}
		}

		int skipped = 0;
		// 同一条外键会写在两端表上（自引用外键在同一张表上写两遍），按原文去重，避免跳过的边被重复计数
		Set<String> seenPairs = new TreeSet<>();
		for (Document document : documentsByName.values()) {
			// foreignKey 形如 "a.x=b.y、c.z=d.w"；没有外键的表 metadata 里是空串或没有这个键
			Object foreignKey = document.getMetadata().get(FOREIGN_KEY);
			if (foreignKey == null) {
				continue;
			}
			// 按顿号拆成一段段 "a.x=b.y"
			for (String pair : String.valueOf(foreignKey).split(FOREIGN_KEY_SEPARATOR)) {
				if (!StringUtils.hasText(pair) || !seenPairs.add(pair.trim())) {
					continue;
				}
				// 解析失败（格式不对）或端点不存在/自环，都算一条被丢弃的边
				String[] refs = parseForeignKeyPair(pair);
				if (refs == null || !isUsableEdge(tables, refs[0], refs[2])) {
					skipped++;
					continue;
				}
				// putEdge 会按 key 去重：生产代码在两张表上各写一遍同一条外键，这里只留一条
				putEdge(edges, new SchemaEdge(refs[0], refs[1], refs[2], refs[3], EdgeType.FOREIGN_KEY,
						FOREIGN_KEY_CONFIDENCE));
			}
		}
		return skipped;
	}

	private static int addLogicalRelationEdges(Map<String, TableNode> tables, List<LogicalRelation> logicalRelations,
			Map<String, SchemaEdge> edges) {
		// 数据源可能没有任何逻辑关系；空集合和 null 都表示“没有”
		if (logicalRelations == null) {
			return 0;
		}
		int skipped = 0;
		for (LogicalRelation relation : logicalRelations) {
			// 逻辑关系是用户手填的，字段可能空；先 trim，空值当无效
			String fromTable = normalize(relation.getSourceTableName());
			String fromColumn = normalize(relation.getSourceColumnName());
			String toTable = normalize(relation.getTargetTableName());
			String toColumn = normalize(relation.getTargetColumnName());
			// 任一字段为空，或表不在当前图里，都丢弃并计数
			if (fromTable == null || fromColumn == null || toTable == null || toColumn == null
					|| !isUsableEdge(tables, fromTable, toTable)) {
				skipped++;
				continue;
			}
			// 逻辑关系可信度 0.9：比物理外键低，比命名推断高
			putEdge(edges, new SchemaEdge(fromTable, fromColumn, toTable, toColumn, EdgeType.LOGICAL_RELATION,
					LOGICAL_RELATION_CONFIDENCE));
		}
		return skipped;
	}

	/**
	 * 按列名推断关系。宁可漏，不要错：只处理代表表的 {@code _id} / {@code _key}
	 * 列；目标列必须单独构成候选表的主键（多对一）；类型不一致直接丢弃。
	 */
	private static void addNamingInferenceEdges(Map<String, TableNode> tables, ColumnIndex index,
			int maxNamingCandidates, Map<String, SchemaEdge> edges) {
		// 遍历所有表（TreeMap，按表名有序）
		for (TableNode source : tables.values()) {
			// 派生表不参与推断：user_info_daily 这类分表会把边搅乱
			if (!source.isRepresentative()) {
				continue;
			}
			// 没有列文档的表推断不了（例如列查询没覆盖到）
			Map<String, ColumnInfo> sourceColumns = index.columnsByTable.get(source.getName());
			if (sourceColumns == null) {
				continue;
			}
			// 只看这张表的每一列
			for (ColumnInfo sourceColumn : sourceColumns.values()) {
				String lowerColumn = sourceColumn.getName().toLowerCase(Locale.ROOT);
				// 只有 _id / _key 后缀才推断；前缀就是去掉后缀的部分，例如 user_id → user
				String prefix = namingPrefix(lowerColumn);
				if (prefix == null) {
					continue;
				}

				// 同一个列可能从三条路找到同一张候选表，用 tableName → 最高分候选 去重
				Map<String, Candidate> candidates = new TreeMap<>();

				// 候选来源 ①：别的表也有同名列，例如 trade_order.user_id
				for (String table : index.tablesByColumn.getOrDefault(lowerColumn, Collections.<String>emptySet())) {
					addCandidate(tables, index, candidates, source, sourceColumn, table, lowerColumn, prefix);
				}
				// 候选来源 ②：别的表把这一列当主键，例如 user_info 的主键是 user_id
				for (String table : index.tablesByPrimaryColumn.getOrDefault(lowerColumn,
						Collections.<String>emptySet())) {
					addCandidate(tables, index, candidates, source, sourceColumn, table, lowerColumn, prefix);
				}
				// 候选来源 ③：表名等于前缀或 前缀_info，例如 user_id → user / user_info
				Set<String> prefixTables = new TreeSet<>();
				addIfPresent(prefixTables, index.tableNamesByLowerName.get(prefix));
				addIfPresent(prefixTables, index.tableNamesByLowerName.get(prefix + "_info"));
				for (String table : prefixTables) {
					addCandidate(tables, index, candidates, source, sourceColumn, table, lowerColumn, prefix);
				}

				// 每个列最多留 N 个候选：排序后截断，避免一对多爆炸
				addTopCandidates(edges, source.getName(), sourceColumn.getName(), candidates, maxNamingCandidates);
			}
		}
	}

	private static void addCandidate(Map<String, TableNode> tables, ColumnIndex index,
			Map<String, Candidate> candidates, TableNode source, ColumnInfo sourceColumn, String candidateTable,
			String lowerColumn, String prefix) {
		// 候选表必须存在、必须是代表表、不能是自己
		TableNode candidateNode = tables.get(candidateTable);
		if (candidateNode == null || !candidateNode.isRepresentative()
				|| candidateNode.getName().equals(source.getName())) {
			return;
		}
		// 目标列：候选表里有同名列就用同名列，否则退回它的 id 列；两者都没有就放弃
		ColumnInfo targetColumn = findTargetColumn(index, candidateTable, lowerColumn);
		// 类型对不上直接放弃（缺类型时视为通过，不因为元数据缺失丢掉整张表）
		if (targetColumn == null || !typesCompatible(sourceColumn.getType(), targetColumn.getType())) {
			return;
		}

		// 目标列必须单独构成候选表的主键：候选表里每个值只有一行，JOIN 是多对一，不会放大行数。
		// 两张事实表碰巧同名的列（dwd_trade_pay_di.buyer_id = dws_trade_user_pay_1m.buyer_id）、
		// 复合主键里的一列（dws_trade_user_order_1d 的主键是 stat_date + buyer_id）都不连
		if (!isSingleColumnPrimaryKey(index, candidateTable, targetColumn.getName())) {
			return;
		}

		// 打分：基础 0.5；表名命中前缀/前缀_info +0.1；目标列与源列同名 +0.1；最高 0.7
		double score = NAMING_CONFIDENCE_BASE;
		if (matchesPrefix(candidateTable, prefix)) {
			score += NAMING_CONFIDENCE_BONUS;
		}
		if (targetColumn.getName().equalsIgnoreCase(sourceColumn.getName())) {
			score += NAMING_CONFIDENCE_BONUS;
		}
		score = Math.min(score, NAMING_CONFIDENCE_MAX);

		// 同一张候选表可能被三条路各找到一次，只留分数最高的那条
		Candidate existing = candidates.get(candidateTable);
		if (existing == null || score > existing.getScore()) {
			candidates.put(candidateTable, new Candidate(candidateTable, targetColumn.getName(), score));
		}
	}

	private static void addTopCandidates(Map<String, SchemaEdge> edges, String sourceTable, String sourceColumn,
			Map<String, Candidate> candidates, int maxNamingCandidates) {
		// 分数降序；同分按表名字典序，保证每次截断结果一样
		List<Candidate> sorted = new ArrayList<>(candidates.values());
		sorted.sort(Comparator.comparingDouble(Candidate::getScore).reversed().thenComparing(Candidate::getTable));
		int limit = Math.min(maxNamingCandidates, sorted.size());
		// 只保留前 N 个，剩下的候选直接丢弃，不生成边
		for (int i = 0; i < limit; i++) {
			Candidate candidate = sorted.get(i);
			putEdge(edges, new SchemaEdge(sourceTable, sourceColumn, candidate.getTable(), candidate.getColumn(),
					EdgeType.NAMING_INFERENCE, candidate.getScore()));
		}
	}

	private static ColumnInfo findTargetColumn(ColumnIndex index, String tableName, String lowerColumn) {
		Map<String, ColumnInfo> columns = index.columnsByTable.get(tableName);
		if (columns == null) {
			return null;
		}
		ColumnInfo sameName = columns.get(lowerColumn);
		return sameName != null ? sameName : columns.get(ID_COLUMN);
	}

	private static boolean isSingleColumnPrimaryKey(ColumnIndex index, String tableName, String columnName) {
		Set<String> primaryColumns = index.primaryColumnsByTable.get(tableName);
		return primaryColumns != null && primaryColumns.size() == 1
				&& primaryColumns.contains(columnName.toLowerCase(Locale.ROOT));
	}

	private static boolean typesCompatible(String left, String right) {
		return !StringUtils.hasText(left) || !StringUtils.hasText(right) || left.equalsIgnoreCase(right);
	}

	private static String namingPrefix(String lowerColumn) {
		String prefix = null;
		if (lowerColumn.endsWith(ID_SUFFIX)) {
			prefix = lowerColumn.substring(0, lowerColumn.length() - ID_SUFFIX.length());
		}
		else if (lowerColumn.endsWith(KEY_SUFFIX)) {
			prefix = lowerColumn.substring(0, lowerColumn.length() - KEY_SUFFIX.length());
		}
		return StringUtils.hasText(prefix) ? prefix : null;
	}

	private static boolean matchesPrefix(String tableName, String prefix) {
		String lowerTableName = tableName.toLowerCase(Locale.ROOT);
		return lowerTableName.equals(prefix) || lowerTableName.equals(prefix + "_info");
	}

	private static boolean isUsableEdge(Map<String, TableNode> tables, String fromTable, String toTable) {
		return tables.containsKey(fromTable) && tables.containsKey(toTable) && !fromTable.equals(toTable);
	}

	/**
	 * 同一对列只保留一条边：置信度高者优先；置信度相同按来源优先级（物理外键 > 逻辑关系 > 命名推断）。
	 */
	private static void putEdge(Map<String, SchemaEdge> edges, SchemaEdge candidate) {
		// 同一对列已经有一条边了：比较后决定是否替换
		SchemaEdge existing = edges.get(candidate.key());
		if (existing == null || shouldReplace(existing, candidate)) {
			edges.put(candidate.key(), candidate);
		}
	}

	private static boolean shouldReplace(SchemaEdge existing, SchemaEdge candidate) {
		// 置信度不同：高者胜（物理外键 1.0 不会被命名推断 0.7 覆盖）
		if (candidate.getConfidence() != existing.getConfidence()) {
			return candidate.getConfidence() > existing.getConfidence();
		}
		// 置信度相同：按枚举顺序，FOREIGN_KEY > LOGICAL_RELATION > NAMING_INFERENCE
		return candidate.getType().ordinal() < existing.getType().ordinal();
	}

	/**
	 * 解析 {@code a.x=b.y}；两端都取最后两段，容忍 {@code schema.a.x}。
	 * @return {@code [fromTable, fromColumn, toTable, toColumn]}，格式不对返回 null
	 */
	private static String[] parseForeignKeyPair(String pair) {
		// "trade_order.user_id=user_info.user_id" → 按等号拆成左右两端
		String[] sides = pair.split("=");
		if (sides.length != 2) {
			return null;
		}
		// 两端各自解析成 表 + 列
		String[] left = parseColumnRef(sides[0]);
		String[] right = parseColumnRef(sides[1]);
		if (left == null || right == null) {
			return null;
		}
		// 拍平成 [源表, 源列, 目标表, 目标列]，方便调用方一次取用
		return new String[] { left[0], left[1], right[0], right[1] };
	}

	private static String[] parseColumnRef(String reference) {
		// 支持 a.x 和 schema.a.x 两种写法：都取最后两段
		String trimmed = reference.trim();
		int lastDot = trimmed.lastIndexOf('.');
		// 没有点、点在开头（表名为空）、点在结尾（列为空）都算格式错误
		if (lastDot <= 0 || lastDot == trimmed.length() - 1) {
			return null;
		}
		// 找到列名前面那个点，两段之间就是表名；没有第二个点则表名从字符串开头算起
		int previousDot = trimmed.lastIndexOf('.', lastDot - 1);
		String table = trimmed.substring(previousDot + 1, lastDot).trim();
		String column = trimmed.substring(lastDot + 1).trim();
		if (!StringUtils.hasText(table) || !StringUtils.hasText(column)) {
			return null;
		}
		return new String[] { table, column };
	}

	private static void addIfPresent(Set<String> tables, String tableName) {
		if (tableName != null) {
			tables.add(tableName);
		}
	}

	private static String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static String normalize(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	/**
	 * 列索引：按表查列、按列反查表、按主键列反查表、按小写表名查真实表名。
	 */
	private static final class ColumnIndex {

		private final Map<String, Map<String, ColumnInfo>> columnsByTable = new TreeMap<>();

		private final Map<String, Set<String>> tablesByColumn = new TreeMap<>();

		private final Map<String, Set<String>> tablesByPrimaryColumn = new TreeMap<>();

		private final Map<String, String> tableNamesByLowerName = new TreeMap<>();

		/** 表名-> 主键列(小写)。复合主键有多列。 */
		private final Map<String, Set<String>> primaryColumnsByTable = new TreeMap<>();

	}

	private static final class ColumnInfo {

		private final String name;

		private final String type;

		private ColumnInfo(String name, String type) {
			this.name = name;
			this.type = type;
		}

		private String getName() {
			return name;
		}

		private String getType() {
			return type;
		}

	}

	private static final class Candidate {

		private final String table;

		private final String column;

		private final double score;

		private Candidate(String table, String column, double score) {
			this.table = table;
			this.column = column;
			this.score = score;
		}

		private String getTable() {
			return table;
		}

		private String getColumn() {
			return column;
		}

		private double getScore() {
			return score;
		}

	}

}
