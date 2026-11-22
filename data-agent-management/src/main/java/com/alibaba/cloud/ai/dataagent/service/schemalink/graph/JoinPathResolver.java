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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * 外键多跳补全：在表关系图上找一棵连通全部终端的近似最小 Steiner 树（Takahashi–Matsuyama 最短路启发式，近似比 2）。
 * <ol>
 * <li>终端去重；图上没有、或不是家族代表表的终端剔除（记入 missingTerminals）；</li>
 * <li>从名字最小的终端起步建树；每轮以树上所有点为源跑一次多源 Dijkstra，把离树最近的终端（距离相同按表名）沿最短路接入；</li>
 * <li>没有终端可达时，从剩下名字最小的终端另起一棵树（连通分量数 +1）；</li>
 * <li>一条路径会让补进来的中间表超过 {@code maxAddedTables} 时，跳过这个终端（记入 skippedTerminals）。</li>
 * </ol>
 * 树的叶子一定是终端（每条路径都以终端结尾），不需要剪枝。只经过家族代表表；所有打平按表名、边的去重键排序，同样的输入一定得到同样的结果。 纯函数，不依赖 Spring 容器。
 */
public final class JoinPathResolver {

	private JoinPathResolver() {
	}

	/**
	 * @param graph 表关系图
	 * @param terminals 必须连起来的表（通常是精选后的表）
	 * @param maxAddedTables 最多补进来几张中间表
	 */
	public static JoinPlan resolve(SchemaGraph graph, Collection<String> terminals, int maxAddedTables) {
		// 1. 终端：去重、排序；不在图上或不是代表表的剔除
		Set<String> pending = new TreeSet<>();
		List<String> missing = new ArrayList<>();
		for (String terminal : new TreeSet<>(terminals)) {
			TableNode node = graph.node(terminal);
			if (node != null && node.isRepresentative()) {
				pending.add(terminal);
			}
			else {
				missing.add(terminal);
			}
		}
		List<String> usedTerminals = new ArrayList<>(pending);

		List<SchemaEdge> treeEdges = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		Set<String> added = new TreeSet<>();
		int components = 0;
		// 当前这棵树的点；另起一棵树时清空
		Set<String> currentTree = new HashSet<>();

		while (!pending.isEmpty()) {
			if (currentTree.isEmpty()) {
				// 2. 起步：剩下名字最小的终端
				String start = pending.iterator().next();
				pending.remove(start);
				currentTree.add(start);
				components++;
				continue;
			}
			// 3. 以当前树为源跑多源 Dijkstra，取最近的终端
			ShortestPaths paths = dijkstra(graph, currentTree);
			String nearest = null;
			for (String terminal : pending) {
				Double d = paths.dist.get(terminal);
				if (d != null && (nearest == null || d < paths.dist.get(nearest))) {
					nearest = terminal;
				}
			}
			if (nearest == null) {
				// 4. 剩下的终端都到不了这棵树：另起一棵
				currentTree.clear();
				continue;
			}
			pending.remove(nearest);
			// 5. 回溯路径，统计会新补进来的中间表；超预算就跳过这个终端
			List<SchemaEdge> path = paths.pathTo(nearest);
			List<String> newTables = new ArrayList<>();
			for (String table : paths.nodesOnPath(nearest)) {
				if (!currentTree.contains(table) && !table.equals(nearest) && !usedTerminals.contains(table)) {
					newTables.add(table);
				}
			}
			if (added.size() + newTables.size() > maxAddedTables) {
				skipped.add(nearest);
				continue;
			}
			for (SchemaEdge edge : path) {
				treeEdges.add(edge);
				currentTree.add(edge.getFromTable());
				currentTree.add(edge.getToTable());
			}
			added.addAll(newTables);
		}
		return new JoinPlan(usedTerminals, new ArrayList<>(added), treeEdges, missing, components, skipped);
	}

	/** 多源 Dijkstra：源点距离为 0；只经过家族代表表；距离相同按表名出队，前驱边相同时按边的去重键取小的。 */
	private static ShortestPaths dijkstra(SchemaGraph graph, Set<String> sources) {
		ShortestPaths result = new ShortestPaths();
		PriorityQueue<QueueEntry> queue = new PriorityQueue<>();
		for (String source : new TreeSet<>(sources)) {
			result.dist.put(source, 0.0);
			queue.add(new QueueEntry(0.0, source));
		}
		Set<String> settled = new HashSet<>();
		while (!queue.isEmpty()) {
			QueueEntry top = queue.poll();
			String table = top.table;
			if (!settled.add(table)) {
				continue;
			}
			double d = top.dist;
			// 邻接表已按代价、去重键排序：遍历顺序固定
			for (SchemaEdge edge : graph.edgesOf(table)) {
				String next = edge.getFromTable().equals(table) ? edge.getToTable() : edge.getFromTable();
				TableNode node = graph.node(next);
				if (node == null || !node.isRepresentative() || settled.contains(next)) {
					continue;
				}
				double nd = d + edge.cost();
				Double old = result.dist.get(next);
				SchemaEdge oldEdge = result.prevEdge.get(next);
				boolean better = old == null || nd < old - 1e-12
						|| (Math.abs(nd - old) <= 1e-12 && oldEdge != null && edge.key().compareTo(oldEdge.key()) < 0);
				if (better) {
					result.dist.put(next, nd);
					result.prevEdge.put(next, edge);
					result.prevNode.put(next, table);
					queue.add(new QueueEntry(nd, next));
				}
			}
		}
		return result;
	}

	/** 优先队列的元素：距离小的先出队，距离相同按表名。 */
	private static final class QueueEntry implements Comparable<QueueEntry> {

		private final double dist;

		private final String table;

		QueueEntry(double dist, String table) {
			this.dist = dist;
			this.table = table;
		}

		@Override
		public int compareTo(QueueEntry other) {
			int byDist = Double.compare(dist, other.dist);
			return byDist != 0 ? byDist : table.compareTo(other.table);
		}

	}

	private static final class ShortestPaths {

		private final Map<String, Double> dist = new HashMap<>();

		private final Map<String, SchemaEdge> prevEdge = new HashMap<>();

		private final Map<String, String> prevNode = new HashMap<>();

		/** 从源（树）到 target 的边，按从树出发的顺序。 */
		List<SchemaEdge> pathTo(String target) {
			List<SchemaEdge> path = new ArrayList<>();
			for (String at = target; prevEdge.containsKey(at); at = prevNode.get(at)) {
				path.add(0, prevEdge.get(at));
			}
			return path;
		}

		/** 路径上的点（含两端）。 */
		List<String> nodesOnPath(String target) {
			List<String> nodes = new ArrayList<>();
			String at = target;
			nodes.add(at);
			while (prevNode.containsKey(at)) {
				at = prevNode.get(at);
				nodes.add(0, at);
			}
			return nodes;
		}

	}

}
