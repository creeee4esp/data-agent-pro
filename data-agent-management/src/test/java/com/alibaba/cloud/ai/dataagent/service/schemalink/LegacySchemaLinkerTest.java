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
package com.alibaba.cloud.ai.dataagent.service.schemalink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

@ExtendWith(MockitoExtension.class)
class LegacySchemaLinkerTest {

	private static final Integer DATASOURCE_ID = 7;

	private static final String QUERY = "查询2025年6月的订单金额";

	@Mock
	private SchemaService schemaService;

	@InjectMocks
	private LegacySchemaLinker linker;

	@Test
	void link_returnsTablesFromVectorRecallAndAllColumnsOfThoseTables() {
		List<Document> tableDocs = Arrays.asList(tableDocument("orders"), tableDocument("users"));
		List<Document> columnDocs = Arrays.asList(new Document("orders.amount"), new Document("users.id"));
		when(schemaService.getTableDocumentsByDatasource(DATASOURCE_ID, QUERY)).thenReturn(tableDocs);
		when(schemaService.getColumnDocumentsByTableName(DATASOURCE_ID, Arrays.asList("orders", "users")))
			.thenReturn(columnDocs);

		LinkedSchema result = linker.link(request(null));

		assertEquals(tableDocs, result.getTableDocuments());
		assertEquals(columnDocs, result.getColumnDocuments());
		assertEquals(Arrays.asList("orders", "users"), result.getTableNames());
		assertEquals(LegacySchemaLinker.NAME, result.getDiagnostics().getLinkerName());
		assertTrue(result.getDiagnostics().getElapsedMillis() >= 0);
		verify(schemaService).getTableDocumentsByDatasource(DATASOURCE_ID, QUERY);
		verify(schemaService).getColumnDocumentsByTableName(DATASOURCE_ID, Arrays.asList("orders", "users"));
		verifyNoMoreInteractions(schemaService);
	}

	@Test
    void link_noTables_returnsEmptyAndStillQueriesColumnsWithEmptyList() {
        // Existing behaviour (locked by SchemaRecallNodeTest): columns are queried even
        // when no table was recalled.
        when(schemaService.getTableDocumentsByDatasource(DATASOURCE_ID, QUERY)).thenReturn(Collections.emptyList());
        when(schemaService.getColumnDocumentsByTableName(DATASOURCE_ID, Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        LinkedSchema result = linker.link(request(null));

        assertTrue(result.getTableDocuments().isEmpty());
        assertTrue(result.getColumnDocuments().isEmpty());
        assertTrue(result.getTableNames().isEmpty());
        verify(schemaService).getColumnDocumentsByTableName(DATASOURCE_ID, Collections.emptyList());
    }

	@Test
	void link_tableDocumentWithoutName_skipsItWhenQueryingColumns() {
		Map<String, Object> noName = new HashMap<>();
		noName.put("other", "value");
		List<Document> tableDocs = Arrays.asList(tableDocument("users"), new Document("doc without name", noName));
		when(schemaService.getTableDocumentsByDatasource(DATASOURCE_ID, QUERY)).thenReturn(tableDocs);
		when(schemaService.getColumnDocumentsByTableName(DATASOURCE_ID, Collections.singletonList("users")))
			.thenReturn(Collections.emptyList());

		LinkedSchema result = linker.link(request(null));

		// The nameless document is still returned as a table document (unchanged
		// behaviour)
		assertEquals(tableDocs, result.getTableDocuments());
		assertEquals(Collections.singletonList("users"), result.getTableNames());
		verify(schemaService).getColumnDocumentsByTableName(DATASOURCE_ID, Collections.singletonList("users"));
	}

	@Test
    void link_ignoresExpandedQueries() {
        // Locks current behaviour; S3 (multi-route recall) will start using expanded
        // queries, and this test will change then.
        when(schemaService.getTableDocumentsByDatasource(DATASOURCE_ID, QUERY)).thenReturn(Collections.emptyList());
        when(schemaService.getColumnDocumentsByTableName(DATASOURCE_ID, Collections.emptyList()))
                .thenReturn(Collections.emptyList());

        linker.link(request(Arrays.asList("6月订单总额", "六月销售额")));

        verify(schemaService, times(1)).getTableDocumentsByDatasource(DATASOURCE_ID, QUERY);
        verify(schemaService).getColumnDocumentsByTableName(DATASOURCE_ID, Collections.emptyList());
        verifyNoMoreInteractions(schemaService);
    }

	@Test
	void request_blankCanonicalQuery_isRejected() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> SchemaLinkRequest.builder().datasourceId(DATASOURCE_ID).canonicalQuery("  ").build());
		assertEquals("canonicalQuery must not be blank", ex.getMessage());
	}

	@Test
	void request_nullExpandedQueries_becomesEmptyList() {
		SchemaLinkRequest request = SchemaLinkRequest.builder()
			.datasourceId(DATASOURCE_ID)
			.canonicalQuery(QUERY)
			.build();

		assertTrue(request.getExpandedQueries().isEmpty());
	}

	private static SchemaLinkRequest request(List<String> expandedQueries) {
		return SchemaLinkRequest.builder()
			.datasourceId(DATASOURCE_ID)
			.agentId("1")
			.canonicalQuery(QUERY)
			.expandedQueries(expandedQueries)
			.build();
	}

	private static Document tableDocument(String tableName) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(DocumentMetadataConstant.NAME, tableName);
		return new Document("table doc", metadata);
	}

}
