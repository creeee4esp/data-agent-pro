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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.TableDTO;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JoinPathEnricherTest {

	@Mock
	private SchemaGraphCache graphCache;

	@Mock
	private SchemaService schemaService;

	private DataAgentProperties properties;

	private JoinPathEnricher enricher;

	@BeforeEach
	void setUp() {
		properties = new DataAgentProperties();
		enricher = new JoinPathEnricher(graphCache, schemaService, properties);
	}

	private static SchemaGraph shopGraph() {
		List<TableNode> nodes = Arrays.asList(new TableNode("user_info", true), new TableNode("trade_order", true),
				new TableNode("trade_order_item", true));
		List<SchemaEdge> edges = Arrays.asList(
				new SchemaEdge("trade_order", "buyer_id", "user_info", "user_id", EdgeType.FOREIGN_KEY, 1.0),
				new SchemaEdge("trade_order_item", "order_id", "trade_order", "order_id", EdgeType.FOREIGN_KEY, 1.0));
		return new SchemaGraph(nodes, edges, 0);
	}

	private static SchemaDTO selected(String... tables) {
		SchemaDTO schema = new SchemaDTO();
		List<TableDTO> list = new ArrayList<>();
		for (String name : tables) {
			TableDTO table = new TableDTO();
			table.setName(name);
			list.add(table);
		}
		schema.setTable(list);
		return schema;
	}

	@Test
	void doesNothingWhenDisabled() {
		SchemaDTO schema = selected("user_info", "trade_order_item");

		assertThat(enricher.enrich(1, schema)).isNull();

		assertThat(schema.getTable()).hasSize(2);
		assertThat(schema.getJoinHints()).isNull();
		verifyNoInteractions(graphCache, schemaService);
	}

	@Test
	void addsIntermediateTableAndJoinHints() {
		properties.getSchemaLinking().getJoinGraph().setEnabled(true);
		when(graphCache.get(1)).thenReturn(shopGraph());
		List<String> added = Collections.singletonList("trade_order");
		when(schemaService.getColumnDocumentsByTableName(1, added)).thenReturn(Collections.emptyList());
		when(schemaService.getTableDocuments(1, added)).thenReturn(Collections.emptyList());
		// buildSchemaFromDocuments 可能按外键多补一张（这里是 user_info），补全只应该追加 trade_order
		doAnswer(invocation -> {
			SchemaDTO extra = invocation.getArgument(3);
			extra.setTable(new ArrayList<>(selected("trade_order", "user_info").getTable()));
			return null;
		}).when(schemaService).buildSchemaFromDocuments(isNull(), anyList(), anyList(), any(SchemaDTO.class));
		SchemaDTO schema = selected("user_info", "trade_order_item");

		JoinPlan plan = enricher.enrich(1, schema);

		assertThat(plan.getAddedTables()).containsExactly("trade_order");
		assertThat(schema.getTable()).extracting(TableDTO::getName)
			.containsExactly("user_info", "trade_order_item", "trade_order");
		assertThat(schema.getJoinHints()).containsExactlyInAnyOrder("trade_order.buyer_id = user_info.user_id",
				"trade_order_item.order_id = trade_order.order_id");
	}

	@Test
	void keepsSchemaWhenGraphFails() {
		properties.getSchemaLinking().getJoinGraph().setEnabled(true);
		when(graphCache.get(eq(1))).thenThrow(new IllegalStateException("vector store down"));
		SchemaDTO schema = selected("user_info", "trade_order_item");

		assertThat(enricher.enrich(1, schema)).isNull();

		assertThat(schema.getTable()).hasSize(2);
		assertThat(schema.getJoinHints()).isNull();
	}

	@Test
	void singleTableNeedsNoEnrichment() {
		properties.getSchemaLinking().getJoinGraph().setEnabled(true);

		assertThat(enricher.enrich(1, selected("user_info"))).isNull();

		verifyNoInteractions(graphCache, schemaService);
	}

}
