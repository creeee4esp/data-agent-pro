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

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 用 Druid SQL 解析器提取一条 SQL 用到的物理表名。
 * <p>
 * 先把 SQL 解析成抽象语法树（AST），再用 {@link SchemaStatVisitor}（访问者模式）遍历语法树、收集表。
 * 与正则相比，能正确处理别名、子查询、CTE、JOIN、注释等情况。
 * <p>
 * 输出的表名经过规范化：去掉引号、去掉库名前缀、转小写，便于和召回结果直接比较。
 */
public final class SqlTableExtractor {

	private SqlTableExtractor() {

	}

	/**
	 * 提取 SQL 中用到的物理表名（规范化后），按首次出现的顺序返回，不含重复。
	 * @param sql 只能包含一条语句
	 * @param dbType SQL 方言，例如 {@link DbType#mysql}
	 * @return 不可修改的表名集合
	 * @throws IllegalArgumentException SQL 为空、无法解析，或包含多条语句
	 */

	public static Set<String> extractTables(String sql, DbType dbType) {
		if (!StringUtils.hasText(sql)) {
			throw new IllegalArgumentException("sql must not be blank");
		}

		List<SQLStatement> statements;
		try {
			statements = SQLUtils.parseStatements(sql, dbType);
		}
		catch (RuntimeException e) {
			// Druid 解析失败会抛 ParserException（RuntimeException 的子类），统一转成参数异常，并带上原 SQL 方便定位
			throw new IllegalArgumentException("Cannot parse SQL: " + sql, e);
		}
		if (statements.size() != 1) {
			throw new IllegalArgumentException("Expected exactly one statement but got " + statements.size());
		}

		// 访问者便利语法属时，把遇到的表登记到getTables()里；别名、子查询别名、CTE名都不会被当成表
		SchemaStatVisitor visitor = SQLUtils.createSchemaStatVisitor(dbType);
		statements.get(0).accept(visitor);
		Set<String> tables = new LinkedHashSet<>();
		for (Map.Entry<TableStat.Name, TableStat> entry : visitor.getTables().entrySet()) {
			tables.add(normalize(entry.getKey().getName()));

		}
		return Collections.unmodifiableSet(tables);

	}

	/** 便捷方法：本项目数据集是 MySQL 方言。 */
	public static Set<String> extractTables(String sql) {
		return extractTables(sql, DbType.mysql);
	}

	/**
	 * 规范化表名：{@code `enterprise_dw`.`Trade_Order`} → {@code trade_order}。
	 */
	static String normalize(String rawName) {
		String name = rawName;
		int dot = name.lastIndexOf('.');
		if (dot >= 0) {
			// 去掉库名 / schema前缀，只保留最后一段
			name = name.substring(dot + 1);
		}
		// SQLUtils.normalize 去掉反引号、双引号、方括号等标识符引号
		return SQLUtils.normalize(name).toLowerCase(Locale.ROOT);
	}

}
