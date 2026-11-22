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
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaGraphBuilderTest {

	private static final DataAgentProperties.SchemaLinking.JoinGraph OPTIONS = new DataAgentProperties.SchemaLinking.JoinGraph();

	@Test
	void buildsOneForeignKeyEdgeEvenWhenBothTablesDeclareIt() {
		List<Document> tables = Arrays.asList(table("trade_order", "trade_order.user_id=user_info.user_id", null),
				table("user_info", "trade_order.user_id=user_info.user_id", null));
		List<Document> columns = Arrays.asList(column("trade_order", "user_id", "BIGINT", false),
				column("user_info", "user_id", "BIGINT", true));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(graph.edgeCount()).isEqualTo(1);
		SchemaEdge edge = graph.getEdges().get(0);
		assertThat(edge.getType()).isEqualTo(EdgeType.FOREIGN_KEY);
		assertThat(edge.getConfidence()).isEqualTo(1.0);
		assertThat(edge.hint()).isEqualTo("trade_order.user_id = user_info.user_id");
		assertThat(edge.cost()).isEqualTo(1.0);
		assertThat(graph.edgesOf("trade_order")).containsExactly(edge);
		assertThat(graph.edgesOf("user_info")).containsExactly(edge);
		assertThat(graph.edgesOf("unknown_table")).isEmpty();
		assertThat(graph.edgeCount(EdgeType.FOREIGN_KEY)).isEqualTo(1);
	}

	@Test
	void parsesForeignKeyWithSchemaPrefix() {
		List<Document> tables = Arrays.asList(
				table("trade_order", "enterprise_dw.trade_order.user_id=enterprise_dw.user_info.user_id", null),
				table("user_info", null, null));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, Collections.emptyList(), Collections.emptyList(),
				noNamingOptions());

		assertThat(graph.getEdges()).extracting(SchemaEdge::hint)
			.containsExactly("trade_order.user_id = user_info.user_id");
	}

	@Test
	void dropsUnknownSelfReferencingAndMalformedForeignKeys() {
		List<Document> tables = Arrays.asList(table("trade_order",
				"trade_order.user_id=missing_table.id、trade_order.parent_id=trade_order.id、bad、a=x.y", null));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, Collections.emptyList(), Collections.emptyList(),
				noNamingOptions());

		assertThat(graph.edgeCount()).isZero();
		assertThat(graph.getSkippedEdgeCount()).isEqualTo(4);
	}

	@Test
	void selfReferencingForeignKeyDeclaredTwiceIsSkippedOnce() {
		// 生产代码把外键写在两端表上；自引用外键因此在同一张表上出现两次
		List<Document> tables = Arrays.asList(table("item_category",
				"item_category.parent_id=item_category.category_id、item_category.parent_id=item_category.category_id",
				null));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, Collections.emptyList(), Collections.emptyList(),
				noNamingOptions());

		assertThat(graph.getSkippedEdgeCount()).isEqualTo(1);
	}

	@Test
	void tableWithoutFamilyIdIsNotRepresentativeWhenIndexHasFamilies() {
		// trade_order_bak 缺 familyId（旧文档混入），其他表有：它不能当代表表参与命名推断
		List<Document> tables = Arrays.asList(table("trade_order", null, "trade_order"),
				table("trade_order_bak", null, null), table("user_info", null, "user_info"));
		List<Document> columns = Arrays.asList(column("trade_order", "user_id", "BIGINT", false),
				column("trade_order_bak", "user_id", "BIGINT", false), column("user_info", "user_id", "BIGINT", true));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(graph.getEdges()).extracting(SchemaEdge::hint)
			.containsExactly("trade_order.user_id = user_info.user_id");
	}

	@Test
	void skipsNamingInferenceBetweenFactTablesSharingAColumn() {
		// 两张事实表都有 buyer_id，但都不是主键：连起来会多对多，放大行数
		List<Document> tables = Arrays.asList(table("dwd_trade_pay_di", null, null),
				table("dwd_trade_refund_di", null, null));
		List<Document> columns = Arrays.asList(column("dwd_trade_pay_di", "buyer_id", "BIGINT", false),
				column("dwd_trade_refund_di", "buyer_id", "BIGINT", false));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(graph.edgeCount()).isZero();
	}

	@Test
	void skipsNamingInferenceToPartOfCompositePrimaryKey() {
		// dws_trade_user_order_1d 的主键是 stat_date + buyer_id：只按 buyer_id 连会一对多
		List<Document> tables = Arrays.asList(table("dwd_trade_pay_di", null, null),
				table("dws_trade_user_order_1d", null, null));
		List<Document> columns = Arrays.asList(column("dwd_trade_pay_di", "buyer_id", "BIGINT", false),
				column("dws_trade_user_order_1d", "stat_date", "DATE", true),
				column("dws_trade_user_order_1d", "buyer_id", "BIGINT", true));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(graph.edgeCount()).isZero();
	}

	@Test
	void entityTableIsNotCrowdedOutByFactTablesWithSameColumn() {
		// trade_order_item.order_id 的候选里，trade_order（主键 order_id）和几张同样有 order_id 的事实表同分；
		// 事实表的 order_id 不是主键，不会成为候选，trade_order 不会因为表名字典序靠后被截掉
		List<Document> tables = Arrays.asList(table("trade_order_item", null, null), table("trade_order", null, null),
				table("dwd_lgs_shipment_di", null, null), table("dwd_trade_order_detail_di", null, null),
				table("dwd_trade_pay_di", null, null));
		List<Document> columns = Arrays.asList(column("trade_order_item", "id", "BIGINT", true),
				column("trade_order_item", "order_id", "BIGINT", false),
				column("trade_order", "order_id", "BIGINT", true),
				column("dwd_lgs_shipment_di", "order_id", "BIGINT", false),
				column("dwd_trade_order_detail_di", "order_id", "BIGINT", false),
				column("dwd_trade_pay_di", "order_id", "BIGINT", false));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(graph.getEdges()).extracting(SchemaEdge::hint)
			.filteredOn(hint -> hint.startsWith("trade_order_item.order_id ="))
			.containsExactly("trade_order_item.order_id = trade_order.order_id");
		// 事实表指向实体表也是正确的多对一边；事实表之间没有边
		assertThat(graph.getEdges()).allMatch(e -> "trade_order".equals(e.getToTable()));
	}

	@Test
	void buildsLogicalRelationEdge() {
		List<Document> tables = Arrays.asList(table("event_log", null, null), table("user_info", null, null));
		LogicalRelation relation = LogicalRelation.builder()
			.sourceTableName("event_log")
			.sourceColumnName("user_id")
			.targetTableName("user_info")
			.targetColumnName("user_id")
			.build();

		SchemaGraph graph = SchemaGraphBuilder.build(tables, Collections.emptyList(),
				Collections.singletonList(relation), noNamingOptions());

		assertThat(graph.edgeCount()).isEqualTo(1);
		SchemaEdge edge = graph.getEdges().get(0);
		assertThat(edge.getType()).isEqualTo(EdgeType.LOGICAL_RELATION);
		assertThat(edge.getConfidence()).isEqualTo(0.9);
		assertThat(edge.hint()).isEqualTo("event_log.user_id = user_info.user_id");
	}

	@Test
	void infersNamingEdgeFromSameColumnAndTableName() {
		List<Document> tables = Arrays.asList(table("trade_order", null, null), table("user_info", null, null));
		List<Document> columns = Arrays.asList(column("trade_order", "user_id", "BIGINT", false),
				column("user_info", "user_id", "BIGINT", true));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(graph.getEdges()).extracting(SchemaEdge::hint)
			.containsExactly("trade_order.user_id = user_info.user_id");
		SchemaEdge edge = graph.getEdges().get(0);
		assertThat(edge.getType()).isEqualTo(EdgeType.NAMING_INFERENCE);
		assertThat(edge.getConfidence()).isEqualTo(0.7);
	}

	@Test
	void skipsNamingInferenceWhenColumnTypesDiffer() {
		List<Document> tables = Arrays.asList(table("trade_order", null, null), table("user_info", null, null));
		List<Document> columns = Arrays.asList(column("trade_order", "user_id", "BIGINT", false),
				column("user_info", "user_id", "VARCHAR", true));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(graph.edgeCount()).isZero();
	}

	@Test
	void skipsNamingInferenceForNonRepresentativeTables() {
		List<Document> tables = Arrays.asList(table("trade_order", null, null),
				table("user_info_daily", null, "user_info"));
		List<Document> columns = Arrays.asList(column("trade_order", "user_id", "BIGINT", false),
				column("user_info_daily", "user_id", "BIGINT", true));

		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(graph.edgeCount()).isZero();
		assertThat(graph.node("user_info_daily").isRepresentative()).isFalse();
	}

	@Test
	void keepsAtMostConfiguredNumberOfNamingCandidates() {
		List<Document> tables = new ArrayList<>();
		tables.add(table("orders", null, null));
		tables.add(table("user", null, null));
		tables.add(table("user_info", null, null));
		tables.add(table("buyer_info", null, null));
		tables.add(table("member_info", null, null));
		List<Document> columns = new ArrayList<>();
		columns.add(column("orders", "user_id", "BIGINT", false));
		for (String candidate : Arrays.asList("user", "user_info", "buyer_info", "member_info")) {
			columns.add(column(candidate, "user_id", "BIGINT", true));
		}

		DataAgentProperties.SchemaLinking.JoinGraph options = new DataAgentProperties.SchemaLinking.JoinGraph();
		options.setMaxNamingCandidates(2);
		SchemaGraph graph = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), options);

		assertThat(graph.getEdges()).extracting(SchemaEdge::hint)
			.filteredOn(hint -> hint.startsWith("orders.user_id ="))
			.containsExactlyInAnyOrder("orders.user_id = user.user_id", "orders.user_id = user_info.user_id");
	}

	@Test
	void producesIdenticalEdgesAcrossBuilds() {
		List<Document> tables = Arrays.asList(table("trade_order", "trade_order.user_id=user_info.user_id", null),
				table("user_info", null, null), table("product_info", null, null));
		List<Document> columns = Arrays.asList(column("trade_order", "user_id", "BIGINT", false),
				column("trade_order", "product_id", "BIGINT", false), column("user_info", "user_id", "BIGINT", true),
				column("product_info", "product_id", "BIGINT", true));

		SchemaGraph first = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);
		SchemaGraph second = SchemaGraphBuilder.build(tables, columns, Collections.emptyList(), OPTIONS);

		assertThat(second.getEdges()).extracting(SchemaEdge::key).containsExactlyElementsOf(keys(first));
		assertThat(second.getEdges()).extracting(SchemaEdge::hint).containsExactlyElementsOf(hints(first));
		assertThat(second.getTables().keySet()).containsExactly("product_info", "trade_order", "user_info");
	}

	private static List<String> keys(SchemaGraph graph) {
		return graph.getEdges().stream().map(SchemaEdge::key).collect(Collectors.toList());
	}

	private static List<String> hints(SchemaGraph graph) {
		return graph.getEdges().stream().map(SchemaEdge::hint).collect(Collectors.toList());
	}

	private static DataAgentProperties.SchemaLinking.JoinGraph noNamingOptions() {
		DataAgentProperties.SchemaLinking.JoinGraph options = new DataAgentProperties.SchemaLinking.JoinGraph();
		options.setMaxNamingCandidates(0);
		return options;
	}

	private static Document table(String name, String foreignKey, String familyId) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.NAME, name);
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE);
		if (foreignKey != null) {
			metadata.put("foreignKey", foreignKey);
		}
		if (familyId != null) {
			metadata.put(DocumentMetadataConstant.FAMILY_ID, familyId);
		}
		return new Document(name, metadata);
	}

	private static Document column(String tableName, String columnName, String type, boolean primary) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.NAME, columnName);
		metadata.put(DocumentMetadataConstant.TABLE_NAME, tableName);
		metadata.put("type", type);
		metadata.put("primary", primary);
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN);
		return new Document(tableName + "." + columnName, metadata);
	}

}
