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
package com.alibaba.cloud.ai.dataagent.service.schemalink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

@ExtendWith(MockitoExtension.class)
class AgenticSchemaLinkerTest {

	private static final Integer DATASOURCE_ID = 7;

	private static final String QUERY = "2025年各会员等级的下单人数";

	@Mock
	private SchemaService schemaService;

	@Mock
	private AgentVectorStoreService vectorStoreService;

	@InjectMocks
	private AgenticSchemaLinker linker;

	@Test
	void link_ranksTablesByFirstHitColumnAndReturnsTablesInThatOrder() {
		when(vectorStoreService.similaritySearch(eq(QUERY), any(Filter.Expression.class), anyInt(), anyDouble()))
			.thenReturn(Arrays.asList(column("user_info"), column("trade_order"), column("user_info")));
		List<String> ranked = Arrays.asList("user_info", "trade_order");
		// 按名字过滤查询不保证顺序，故意倒序返回
		when(schemaService.getTableDocuments(DATASOURCE_ID, ranked))
			.thenReturn(Arrays.asList(table("trade_order"), table("user_info")));
		List<Document> columns = Collections.singletonList(new Document("user_info.member_level"));
		when(schemaService.getColumnDocumentsByTableName(DATASOURCE_ID, ranked)).thenReturn(columns);

		LinkedSchema result = linker.link(SchemaLinkRequest.builder().datasourceId(DATASOURCE_ID).canonicalQuery(QUERY).build());

		assertEquals(ranked, result.getTableNames());
		assertEquals(columns, result.getColumnDocuments());
		assertEquals(AgenticSchemaLinker.NAME, result.getDiagnostics().getLinkerName());
	}

	@Test
	void link_searchesOnlyRepresentativeColumnsOfTheDatasource() {
		ArgumentCaptor<Filter.Expression> filter = ArgumentCaptor.forClass(Filter.Expression.class);
		when(vectorStoreService.similaritySearch(eq(QUERY), filter.capture(), eq(AgenticSchemaLinker.COLUMN_TOP_K),
				eq(0.0)))
			.thenReturn(Collections.emptyList());

		linker.link(SchemaLinkRequest.builder().datasourceId(DATASOURCE_ID).canonicalQuery(QUERY).build());

		String expression = filter.getValue().toString();
		assertTrue(expression.contains(DocumentMetadataConstant.FAMILY_REPRESENTATIVE), expression);
		assertTrue(expression.contains(DocumentMetadataConstant.COLUMN), expression);
		assertTrue(expression.contains(DATASOURCE_ID.toString()), expression);
		verify(schemaService).getColumnDocumentsByTableName(DATASOURCE_ID, Collections.emptyList());
	}

	@Test
	void rankTables_keepsAtMostMaxTables() {
		List<Document> columns = new ArrayList<>();
		for (int i = 0; i < AgenticSchemaLinker.MAX_TABLES + 5; i++) {
			columns.add(column("t" + i));
		}
		List<String> ranked = AgenticSchemaLinker.rankTables(columns);
		assertEquals(AgenticSchemaLinker.MAX_TABLES, ranked.size());
		assertEquals("t0", ranked.get(0));
	}

	private static Document column(String tableName) {
		return Document.builder()
			.text("c; " + tableName)
			.metadata(DocumentMetadataConstant.TABLE_NAME, tableName)
			.build();
	}

	private static Document table(String name) {
		return Document.builder().text(name).metadata(DocumentMetadataConstant.NAME, name).build();
	}

}
