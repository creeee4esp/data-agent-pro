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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

class JoinPathResolverTest {

	private static TableNode rep(String name) {
		return new TableNode(name, true);
	}

	private static SchemaEdge fk(String a, String ac, String b, String bc) {
		return new SchemaEdge(a, ac, b, bc, EdgeType.FOREIGN_KEY, 1.0);
	}

	private static SchemaEdge naming(String a, String ac, String b, String bc, double confidence) {
		return new SchemaEdge(a, ac, b, bc, EdgeType.NAMING_INFERENCE, confidence);
	}

	/** user_info — trade_order — trade_order_item — item_sku — item_category。 */
	private static SchemaGraph shopGraph() {
		List<TableNode> nodes = Arrays.asList(rep("user_info"), rep("trade_order"), rep("trade_order_item"),
				rep("item_sku"), rep("item_category"), rep("pay_order"));
		List<SchemaEdge> edges = Arrays.asList(fk("trade_order", "buyer_id", "user_info", "user_id"),
				fk("trade_order_item", "order_id", "trade_order", "order_id"),
				fk("trade_order_item", "sku_id", "item_sku", "sku_id"),
				fk("item_sku", "category_id", "item_category", "category_id"),
				fk("pay_order", "order_id", "trade_order", "order_id"));
		return new SchemaGraph(nodes, edges, 0);
	}

	@Test
	void addsIntermediateTablesAndJoinHints() {
		JoinPlan plan = JoinPathResolver.resolve(shopGraph(),
				Arrays.asList("user_info", "trade_order_item", "item_category"), 5);

		assertThat(plan.getAddedTables()).containsExactly("item_sku", "trade_order");
		assertThat(plan.getEdges()).extracting(SchemaEdge::hint)
			.containsExactlyInAnyOrder("trade_order.buyer_id = user_info.user_id",
					"trade_order_item.order_id = trade_order.order_id", "trade_order_item.sku_id = item_sku.sku_id",
					"item_sku.category_id = item_category.category_id");
		assertThat(plan.getComponents()).isEqualTo(1);
		assertThat(plan.getMissingTerminals()).isEmpty();
	}

	@Test
	void singleTerminalNeedsNothing() {
		JoinPlan plan = JoinPathResolver.resolve(shopGraph(), Collections.singletonList("trade_order"), 5);

		assertThat(plan.getEdges()).isEmpty();
		assertThat(plan.getAddedTables()).isEmpty();
		assertThat(plan.getComponents()).isEqualTo(1);
	}

	@Test
	void unknownAndNonRepresentativeTerminalsAreReported() {
		List<TableNode> nodes = new ArrayList<>(Arrays.asList(rep("a"), rep("b"), new TableNode("a_bak", false)));
		SchemaGraph graph = new SchemaGraph(nodes, Collections.singletonList(fk("a", "b_id", "b", "b_id")), 0);

		JoinPlan plan = JoinPathResolver.resolve(graph, Arrays.asList("a", "b", "a_bak", "missing"), 5);

		assertThat(plan.getTerminals()).containsExactly("a", "b");
		assertThat(plan.getMissingTerminals()).containsExactly("a_bak", "missing");
		assertThat(plan.getEdges()).hasSize(1);
	}

	@Test
	void disconnectedTerminalsFormSeparateComponents() {
		List<TableNode> nodes = Arrays.asList(rep("a"), rep("b"), rep("c"), rep("d"));
		List<SchemaEdge> edges = Arrays.asList(fk("a", "b_id", "b", "b_id"), fk("c", "d_id", "d", "d_id"));

		JoinPlan plan = JoinPathResolver.resolve(new SchemaGraph(nodes, edges, 0), Arrays.asList("a", "b", "c", "d"),
				5);

		assertThat(plan.getComponents()).isEqualTo(2);
		assertThat(plan.getEdges()).hasSize(2);
	}

	@Test
	void skipsTerminalWhenBudgetExceeded() {
		JoinPlan plan = JoinPathResolver.resolve(shopGraph(), Arrays.asList("user_info", "item_category"), 2);

		// user_info → item_category 要补 trade_order、trade_order_item、item_sku 三张，超过预算 2
		assertThat(plan.getSkippedTerminals()).containsExactly("user_info");
		assertThat(plan.getEdges()).isEmpty();
	}

	@Test
	void prefersFewerHops() {
		// a — x — y — b 三段外键（3.0），a — b 一段命名推断 0.5（1.5）：跳数代价让直连胜出
		List<TableNode> nodes = Arrays.asList(rep("a"), rep("b"), rep("x"), rep("y"));
		List<SchemaEdge> edges = Arrays.asList(fk("a", "x_id", "x", "x_id"), fk("x", "y_id", "y", "y_id"),
				fk("y", "b_id", "b", "b_id"), naming("a", "b_id", "b", "b_id", 0.5));

		JoinPlan plan = JoinPathResolver.resolve(new SchemaGraph(nodes, edges, 0), Arrays.asList("a", "b"), 5);

		assertThat(plan.getEdges()).extracting(SchemaEdge::hint).containsExactly("a.b_id = b.b_id");
		assertThat(plan.getAddedTables()).isEmpty();
	}

	@Test
	void prefersForeignKeyWhenHopsEqual() {
		// 同样一跳：外键（代价 1.0）优于命名推断（1.3）
		List<TableNode> nodes = Arrays.asList(rep("a"), rep("b"));
		List<SchemaEdge> edges = Arrays.asList(fk("a", "b_id", "b", "b_id"), naming("a", "bb_id", "b", "bb_id", 0.7));

		JoinPlan plan = JoinPathResolver.resolve(new SchemaGraph(nodes, edges, 0), Arrays.asList("a", "b"), 5);

		assertThat(plan.getEdges()).extracting(SchemaEdge::getType).containsExactly(EdgeType.FOREIGN_KEY);
	}

	@Test
	void sameInputGivesSameTreeRegardlessOfTerminalOrder() {
		JoinPlan first = JoinPathResolver.resolve(shopGraph(),
				Arrays.asList("item_category", "user_info", "trade_order_item"), 5);
		JoinPlan second = JoinPathResolver.resolve(shopGraph(),
				Arrays.asList("trade_order_item", "item_category", "user_info"), 5);

		assertThat(first.getEdges()).extracting(SchemaEdge::key)
			.containsExactlyElementsOf(
					second.getEdges().stream().map(SchemaEdge::key).collect(java.util.stream.Collectors.toList()));
	}

	/**
	 * 随机小图上与 Dreyfus–Wagner 精确解对照：近似解不超过最优解的 2 倍（理论保证），并统计等于最优解的比例。
	 */
	@Test
	void withinTwiceOptimumOnRandomGraphs() {
		Random random = new Random(42);
		int equal = 0;
		int cases = 300;
		for (int c = 0; c < cases; c++) {
			int n = 6 + random.nextInt(7);
			List<TableNode> nodes = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				nodes.add(rep("t" + i));
			}
			List<SchemaEdge> edges = new ArrayList<>();
			for (int i = 1; i < n; i++) {
				// 先连成一棵随机树保证连通，再随机加边
				int j = random.nextInt(i);
				edges.add(randomEdge(random, "t" + i, "t" + j, edges.size()));
			}
			for (int extra = 0; extra < n; extra++) {
				int i = random.nextInt(n);
				int j = random.nextInt(n);
				if (i != j) {
					edges.add(randomEdge(random, "t" + i, "t" + j, edges.size()));
				}
			}
			SchemaGraph graph = new SchemaGraph(nodes, edges, 0);
			List<String> terminals = new ArrayList<>();
			int k = 2 + random.nextInt(Math.min(4, n - 1));
			while (terminals.size() < k) {
				String t = "t" + random.nextInt(n);
				if (!terminals.contains(t)) {
					terminals.add(t);
				}
			}

			JoinPlan plan = JoinPathResolver.resolve(graph, terminals, n);
			double optimum = DreyfusWagner.optimum(graph, terminals);

			assertThat(plan.getComponents()).isEqualTo(1);
			assertThat(plan.totalCost()).isLessThanOrEqualTo(2 * optimum + 1e-9);
			if (Math.abs(plan.totalCost() - optimum) < 1e-9) {
				equal++;
			}
		}
		// 记录在案：这组随机图上近似解等于最优解的比例
		System.out.println("JoinPathResolver equals optimum in " + equal + " / " + cases + " random cases");
		assertThat(equal).isGreaterThan(cases / 2);
	}

	private static SchemaEdge randomEdge(Random random, String a, String b, int seq) {
		double[] confidences = { 1.0, 0.9, 0.7, 0.6, 0.5 };
		double confidence = confidences[random.nextInt(confidences.length)];
		EdgeType type = confidence == 1.0 ? EdgeType.FOREIGN_KEY
				: confidence == 0.9 ? EdgeType.LOGICAL_RELATION : EdgeType.NAMING_INFERENCE;
		return new SchemaEdge(a, "c" + seq, b, "c" + seq, type, confidence);
	}

	/** Dreyfus–Wagner 精确 Steiner 树代价，只用于测试对照（终端数小）。 */
	private static final class DreyfusWagner {

		static double optimum(SchemaGraph graph, List<String> terminals) {
			List<String> nodes = new ArrayList<>(graph.getTables().keySet());
			int n = nodes.size();
			Map<String, Integer> index = new HashMap<>();
			for (int i = 0; i < n; i++) {
				index.put(nodes.get(i), i);
			}
			double[][] dist = new double[n][n];
			for (double[] row : dist) {
				Arrays.fill(row, Double.POSITIVE_INFINITY);
			}
			for (int i = 0; i < n; i++) {
				dist[i][i] = 0;
			}
			for (SchemaEdge e : graph.getEdges()) {
				int a = index.get(e.getFromTable());
				int b = index.get(e.getToTable());
				dist[a][b] = Math.min(dist[a][b], e.cost());
				dist[b][a] = dist[a][b];
			}
			for (int m = 0; m < n; m++) {
				for (int i = 0; i < n; i++) {
					for (int j = 0; j < n; j++) {
						dist[i][j] = Math.min(dist[i][j], dist[i][m] + dist[m][j]);
					}
				}
			}
			int k = terminals.size();
			double[][] dp = new double[1 << k][n];
			for (double[] row : dp) {
				Arrays.fill(row, Double.POSITIVE_INFINITY);
			}
			for (int t = 0; t < k; t++) {
				for (int v = 0; v < n; v++) {
					dp[1 << t][v] = dist[index.get(terminals.get(t))][v];
				}
			}
			for (int s = 1; s < (1 << k); s++) {
				if (Integer.bitCount(s) < 2) {
					continue;
				}
				for (int v = 0; v < n; v++) {
					for (int a = (s - 1) & s; a > 0; a = (a - 1) & s) {
						dp[s][v] = Math.min(dp[s][v], dp[a][v] + dp[s ^ a][v]);
					}
				}
				for (int v = 0; v < n; v++) {
					for (int u = 0; u < n; u++) {
						dp[s][v] = Math.min(dp[s][v], dp[s][u] + dist[u][v]);
					}
				}
			}
			double best = Double.POSITIVE_INFINITY;
			for (int v = 0; v < n; v++) {
				best = Math.min(best, dp[(1 << k) - 1][v]);
			}
			return best;
		}

	}

}
