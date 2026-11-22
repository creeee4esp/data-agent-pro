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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 只读的表关系图：点表、无向边表、邻接表。
 *
 * <p>
 * 构造时完成排序和统计，之后不再修改，可以安全地在多个请求间共享。
 */
@Getter
public final class SchemaGraph {

	/**
	 * 点表，按表名排序
	 */
	private final Map<String, TableNode> tables;

	/**
	 * 邻接表，按表名排序：每个列表按边的代价、去重键排序。
	 */
	private final Map<String, List<SchemaEdge>> adjacency;

	/**
	 * 全部边，按去重键排序
	 */
	private final List<SchemaEdge> edges;

	/**
	 * 每种来源的边数
	 */
	private final Map<EdgeType, Integer> edgeCountByType;

	/**
	 * 建图时丢弃的边数：指向未知表、自环、格式错误
	 */
	private final int skippedEdgeCount;

	public SchemaGraph(List<TableNode> tableNodes, List<SchemaEdge> edges, int skippedEdgeCount) {
		// 1. 点表：TreeMap 保证按表名遍历时顺序固定
		Map<String, TableNode> tableMap = new TreeMap<>();
		for (TableNode node : tableNodes) {
			tableMap.put(node.getName(), node);
		}
		this.tables = Collections.unmodifiableMap(tableMap);

		// 2. 全部边：按去重键排序，同一份输入每次得到同样的顺序
		List<SchemaEdge> sortedEdges = new ArrayList<>(edges);
		sortedEdges.sort(Comparator.comparing(SchemaEdge::key));
		this.edges = Collections.unmodifiableList(sortedEdges);

		// 3. 邻接表：节点与节点相邻的所有边的集合，key 用 TreeMap 固定顺序
		Map<String, List<SchemaEdge>> adjacencyMap = new TreeMap<>();
		for (String tableName : tableMap.keySet()) {
			adjacencyMap.put(tableName, new ArrayList<>());
		}
		for (SchemaEdge edge : sortedEdges) {
			adjacencyMap.get(edge.getFromTable()).add(edge);
			adjacencyMap.get(edge.getToTable()).add(edge);
		}
		// 4. 每个节点对应的边，按代价排序：最可信的排在最前面
		Comparator<SchemaEdge> byCost = Comparator.comparingDouble(SchemaEdge::cost).thenComparing(SchemaEdge::key);
		for (Map.Entry<String, List<SchemaEdge>> entry : adjacencyMap.entrySet()) {
			entry.getValue().sort(byCost);
			entry.setValue(Collections.unmodifiableList(entry.getValue()));
		}
		this.adjacency = Collections.unmodifiableMap(adjacencyMap);

		// 5. 按来源统计边数：先给每种来源填 0，查不到时返回 0 而不是 null
		Map<EdgeType, Integer> counts = new EnumMap<>(EdgeType.class);
		for (EdgeType type : EdgeType.values()) {
			counts.put(type, 0);
		}
		for (SchemaEdge edge : sortedEdges) {
			counts.merge(edge.getType(), 1, Integer::sum);
		}
		this.edgeCountByType = Collections.unmodifiableMap(counts);

		// 6. 建图阶段丢弃的边数由构造图的一方传入，图本身不重复统计
		this.skippedEdgeCount = skippedEdgeCount;
	}

	public boolean contains(String tableName) {
		return tables.containsKey(tableName);
	}

	public TableNode node(String tableName) {
		return tables.get(tableName);
	}

	/**
	 * 获取一个节点的相邻边
	 */
	public List<SchemaEdge> edgesOf(String tableName) {
		List<SchemaEdge> tableEdges = adjacency.get(tableName);
		return tableEdges == null ? Collections.<SchemaEdge>emptyList() : tableEdges;
	}

	public int edgeCount() {
		return edges.size();
	}

	public int edgeCount(EdgeType type) {
		Integer count = edgeCountByType.get(type);
		return count == null ? 0 : count;
	}

}
