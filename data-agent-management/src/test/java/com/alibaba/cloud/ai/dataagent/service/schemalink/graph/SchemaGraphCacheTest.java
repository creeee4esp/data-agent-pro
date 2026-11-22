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
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchemaGraphCacheTest {

	private static final int TABLE_DOCUMENT_LIMIT = 10_000;

	@Mock
	private AgentVectorStoreService vectorStoreService;

	@Mock
	private DatasourceService datasourceService;

	private DataAgentProperties properties;

	private SchemaGraphCache cache;

	@BeforeEach
	void setUp() {
		properties = new DataAgentProperties();
		cache = new SchemaGraphCache(vectorStoreService, datasourceService, properties);
	}

	@Test
	void loadsGraphOnceWithinTtl() {
		stubDocuments(Arrays.asList(table("trade_order", "trade_order.user_id=user_info.user_id"),
				table("user_info", "trade_order.user_id=user_info.user_id")));
		when(datasourceService.getLogicalRelations(7)).thenReturn(Collections.emptyList());

		SchemaGraph first = cache.get(7);
		SchemaGraph second = cache.get(7);

		assertThat(second).isSameAs(first);
		assertThat(first.getTables().keySet()).containsExactly("trade_order", "user_info");
		assertThat(first.edgeCount()).isEqualTo(1);
		verify(vectorStoreService, times(2)).getDocumentsOnlyByFilter(any(Filter.Expression.class), any(Integer.class));
		verify(datasourceService, times(1)).getLogicalRelations(7);
	}

	@Test
	void reloadsAfterInvalidate() {
		stubDocuments(Collections.singletonList(table("trade_order", null)));
		when(datasourceService.getLogicalRelations(7)).thenReturn(Collections.emptyList());

		cache.get(7);
		cache.invalidate(7);
		cache.get(7);

		verify(vectorStoreService, times(4)).getDocumentsOnlyByFilter(any(Filter.Expression.class), any(Integer.class));
		verify(datasourceService, times(2)).getLogicalRelations(7);
	}

	@Test
	void reloadsWhenTtlIsZero() {
		properties.getSchemaLinking().getJoinGraph().setCacheTtlSeconds(0);
		stubDocuments(Collections.singletonList(table("trade_order", null)));
		when(datasourceService.getLogicalRelations(7)).thenReturn(Collections.emptyList());

		cache.get(7);
		cache.get(7);

		verify(vectorStoreService, times(4)).getDocumentsOnlyByFilter(any(Filter.Expression.class), any(Integer.class));
	}

	@Test
	void fetchesColumnDocumentsInBatches() {
		List<Document> tables = new ArrayList<>();
		for (int i = 0; i < 250; i++) {
			tables.add(table("table_" + i, null));
		}
		stubDocuments(tables);
		when(datasourceService.getLogicalRelations(7)).thenReturn(Collections.emptyList());

		cache.get(7);

		ArgumentCaptor<Integer> topK = ArgumentCaptor.forClass(Integer.class);
		verify(vectorStoreService, times(4)).getDocumentsOnlyByFilter(any(Filter.Expression.class), topK.capture());
		assertThat(topK.getAllValues()).containsExactly(TABLE_DOCUMENT_LIMIT, 5_000, 5_000, 2_500);
	}

	@Test
	void skipsColumnAndRelationQueriesWhenThereAreNoTables() {
		stubDocuments(Collections.emptyList());

		SchemaGraph graph = cache.get(7);

		assertThat(graph.getTables()).isEmpty();
		assertThat(graph.edgeCount()).isZero();
		verify(vectorStoreService, times(1)).getDocumentsOnlyByFilter(any(Filter.Expression.class), any(Integer.class));
		verifyNoInteractions(datasourceService);
	}

	private void stubDocuments(List<Document> tableDocuments) {
		when(vectorStoreService.getDocumentsOnlyByFilter(any(Filter.Expression.class), any(Integer.class)))
			.thenAnswer(invocation -> {
				int topK = invocation.getArgument(1);
				return topK == TABLE_DOCUMENT_LIMIT ? tableDocuments : Collections.<Document>emptyList();
			});
	}

	private static Document table(String name, String foreignKey) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.NAME, name);
		metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE);
		if (foreignKey != null) {
			metadata.put("foreignKey", foreignKey);
		}
		return new Document(name, metadata);
	}

}
