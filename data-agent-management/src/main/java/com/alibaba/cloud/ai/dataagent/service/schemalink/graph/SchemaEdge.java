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

import lombok.Getter;
import lombok.ToString;

/**
 * 表关系图上的一条无向边，表示 {@code fromTable.fromColumn = toTable.toColumn}。
 */
@Getter
@ToString
public final class SchemaEdge {

	private final String fromTable;

	private final String fromColumn;

	private final String toTable;

	private final String toColumn;

	private final EdgeType type;

	/**
	 * 置信度，0～1。物理外键 1.0，逻辑关系 0.9，命名推断 0.5～0.7。
	 */
	private final double confidence;

	/**
	 * 每走一段边的固定代价。没有它时外键边代价为0，"绕5跳" 和 "直连1跳"一样便宜，路径会无谓绕远
	 */
	public static final double HOP_COST = 1.0;

	public SchemaEdge(String fromTable, String fromColumn, String toTable, String toColumn, EdgeType type,
			double confidence) {
		this.fromColumn = fromColumn;
		this.fromTable = fromTable;
		this.toColumn = toColumn;
		this.toTable = toTable;
		this.type = type;
		this.confidence = confidence;
	}

	/**
	 * 求最短路时用的代价：固定的跳数代价 + 可信度越低越贵（外键 1.0、逻辑关系 1.1、命名推断 1.3～1.5）。
	 */
	public double cost() {
		return HOP_COST + (1.0 - confidence);
	}

	/**
	 * 无向边的去重键：两端列引用按字典序排序后拼接，保证 a.x=b.y 和 b.y=a.x 是同一个键。
	 */
	public String key() {
		String left = fromTable + "." + fromColumn;
		String right = toTable + "." + toColumn;
		return left.compareTo(right) <= 0 ? left + "=" + right : right + "=" + left;
	}

	/**
	 * 写进 prompt 的 JOIN 提示，例如 {@code trade_order.user_id = user_info.user_id}。
	 */
	public String hint() {
		return fromTable + "." + fromColumn + " = " + toTable + "." + toColumn;
	}

}
