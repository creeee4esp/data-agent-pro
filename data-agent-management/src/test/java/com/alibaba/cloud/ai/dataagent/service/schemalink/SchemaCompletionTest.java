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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

class SchemaCompletionTest {

	private static final LinkedHashSet<String> EXISTING = new LinkedHashSet<>(
			Arrays.asList("trade_order", "trade_order_item", "item_sku"));

	@Test
	void parseTables_takesLastJsonAndKeepsOnlyExistingTablesWithRealCase() {
		String output = "思考：先看 {\"tables\": [\"x\"]}，最终结果：\n"
				+ "{\"tables\": [\"TRADE_ORDER\", \"trade_order_item\", \"no_such_table\", \"trade_order\"]}";
		assertEquals(Arrays.asList("trade_order", "trade_order_item"),
				SchemaCompletionAgent.parseTables(output, EXISTING));
	}

	@Test
	void parseTables_returnsEmptyForNullOrMalformedOutput() {
		assertTrue(SchemaCompletionAgent.parseTables(null, EXISTING).isEmpty());
		assertTrue(SchemaCompletionAgent.parseTables("我认为需要 trade_order", EXISTING).isEmpty());
	}

	@Test
	void formatFamilies_listsOnlyRepresentativesWithMemberCount() {
		List<Document> tables = Arrays.asList(table("trade_order", "trade_order", "交易订单主表"),
				table("trade_order_202503", "trade_order", "交易订单主表"), table("trade_order_bak", "trade_order", ""),
				table("item_sku", "item_sku", ""));
		assertEquals("item_sku\ntrade_order 交易订单主表（另有 2 张分表/备份/副本）\n", SchemaCompletionAgent.formatFamilies(tables));
	}

	@Test
	void retrieveSchema_excludesSeenColumnsAndStopsAfterBudget() {
		AgentVectorStoreService store = mock(AgentVectorStoreService.class);
		when(store.similaritySearch(anyString(), any(Filter.Expression.class), anyInt(), anyDouble()))
			.thenReturn(Arrays.asList(column("trade_order", "pay_amount"), column("item_sku", "category_id")));
		SchemaCompletionTools tools = new SchemaCompletionTools(1, mock(SchemaService.class), store,
				Collections.singletonList(column("trade_order", "pay_amount")));

		String first = tools.retrieveSchema("品类");
		assertTrue(first.contains("# item_sku") && !first.contains("pay_amount"), first);
		assertEquals("没有新的相关列。", tools.retrieveSchema("品类"));

		for (int i = 2; i < SchemaCompletionTools.MAX_CALLS; i++) {
			tools.retrieveSchema("x");
		}
		assertEquals(SchemaCompletionTools.BUDGET_EXHAUSTED, tools.retrieveSchema("x"));
		assertEquals(SchemaCompletionTools.BUDGET_EXHAUSTED, tools.describeTables(Collections.singletonList("a")));
		// 含超出上限、未执行的那一次
		assertEquals(SchemaCompletionTools.MAX_CALLS + 1, tools.getCallsByTool().get("retrieve_schema").intValue());
		assertEquals(1, tools.getCallsByTool().get("describe_tables").intValue());
	}

	private static Document table(String name, String familyId, String description) {
		Map<String, Object> m = new HashMap<>();
		m.put(DocumentMetadataConstant.NAME, name);
		m.put(DocumentMetadataConstant.FAMILY_ID, familyId);
		m.put("description", description);
		return new Document(name, m);
	}

	private static Document column(String table, String name) {
		Map<String, Object> m = new HashMap<>();
		m.put(DocumentMetadataConstant.TABLE_NAME, table);
		m.put(DocumentMetadataConstant.NAME, name);
		m.put("type", "bigint");
		m.put("description", "");
		return new Document(table + "." + name, m);
	}

}
