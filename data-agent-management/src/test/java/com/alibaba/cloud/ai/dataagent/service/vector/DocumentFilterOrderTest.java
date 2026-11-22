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
package com.alibaba.cloud.ai.dataagent.service.vector;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentFilterOrderTest {

	@Test
	void ordersByTableNameThenName() {
		List<Document> documents = new ArrayList<>(Arrays.asList(document("c3", "t2", "b"), document("c1", "t1", "b"),
				document("c2", "t1", "a"), documentWithoutTable("c4", "a")));

		documents.sort(DocumentFilterOrder.ORDER);

		// tableName 缺失时按字符串 "null" 参与排序，排在 t1/t2 前面
		assertThat(documents).extracting(Document::getText).containsExactly("c4", "c2", "c1", "c3");
	}

	@Test
	void fallsBackToTextThenId() {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("tableName", "t1");
		metadata.put("name", "c");
		List<Document> documents = new ArrayList<>(Arrays.asList(new Document("id2", "same", metadata),
				new Document("id1", "same", metadata), new Document("id0", "other", metadata)));

		documents.sort(DocumentFilterOrder.ORDER);

		assertThat(documents).extracting(Document::getId).containsExactly("id0", "id1", "id2");
	}

	private static Document document(String text, String tableName, String name) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("tableName", tableName);
		metadata.put("name", name);
		return new Document(text, metadata);
	}

	private static Document documentWithoutTable(String text, String name) {
		Map<String, Object> metadata = new HashMap<>();
		metadata.put("name", name);
		return new Document(text, metadata);
	}

}
