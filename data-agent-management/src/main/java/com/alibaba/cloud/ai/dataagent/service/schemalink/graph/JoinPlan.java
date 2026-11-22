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

import java.util.Collections;
import java.util.List;

import lombok.Getter;
import lombok.ToString;

/**
 * 外键多跳补全的结果：要补进来的中间表、连通终端用到的边（即 JOIN 提示），以及没能连上的终端。
 */
@Getter
@ToString
public final class JoinPlan {

	/** 参与补全的终端（在图上的代表表），按表名排序。 */
	private final List<String> terminals;

	/** 补进来的中间表（不是终端、但路径要经过），按表名排序。 */
	private final List<String> addedTables;

	/** 树上的边，按加入顺序；每条边的 {@link SchemaEdge#hint()} 就是一条 JOIN 提示。 */
	private final List<SchemaEdge> edges;

	/** 图上没有、或不是代表表的终端（例如噪声表），没有参与补全。 */
	private final List<String> missingTerminals;

	/** 连通分量数：1 表示全部终端连通；大于 1 表示有终端之间没有路径，各自成组。 */
	private final int components;

	/** 因补表预算不够而没有接入的终端。 */
	private final List<String> skippedTerminals;

	public JoinPlan(List<String> terminals, List<String> addedTables, List<SchemaEdge> edges,
			List<String> missingTerminals, int components, List<String> skippedTerminals) {
		this.terminals = Collections.unmodifiableList(terminals);
		this.addedTables = Collections.unmodifiableList(addedTables);
		this.edges = Collections.unmodifiableList(edges);
		this.missingTerminals = Collections.unmodifiableList(missingTerminals);
		this.components = components;
		this.skippedTerminals = Collections.unmodifiableList(skippedTerminals);
	}

	/** 树的总代价。 */
	public double totalCost() {
		double sum = 0;
		for (SchemaEdge edge : edges) {
			sum += edge.cost();
		}
		return sum;
	}

}
