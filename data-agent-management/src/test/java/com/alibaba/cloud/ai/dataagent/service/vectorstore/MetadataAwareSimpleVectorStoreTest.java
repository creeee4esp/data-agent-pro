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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.env.StandardEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataAwareSimpleVectorStoreTest {

	@Test
	void deleteByMetadata_removesOnlyExactMetadataMatches() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(new Document("one", Map.of("agentId", "1")), new Document("two", Map.of("agentId", "2"))));

		assertEquals(1, store.deleteByMetadata(Map.of("agentId", 1)));
		assertEquals(0, store.deleteByMetadata(Map.of("agentId", "1")));
		assertEquals(1, store.deleteByMetadata(Map.of("agentId", "2")));
	}

	@Test
	void similaritySearch_tiedScores_areOrderedDeterministicallyAndTruncatedConsistently() {
		// 4 篇文本完全相同的文档相似度打平；topK=2 时，每次都应返回 metadata 排序最靠前的两篇（a、b），
		// 而不是取决于内部 Map 遍历顺序的任意两篇
		for (int run = 0; run < 5; run++) {
			MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
			store.add(List.of(new Document("用户基础信息表", Map.of("name", "d")),
					new Document("用户基础信息表", Map.of("name", "b")), new Document("用户基础信息表", Map.of("name", "c")),
					new Document("用户基础信息表", Map.of("name", "a")), new Document("订单表", Map.of("name", "z"))));

			List<Document> top = store.similaritySearch(
					org.springframework.ai.vectorstore.SearchRequest.builder().query("用户").topK(2).build());

			assertThat(top).extracting(d -> d.getMetadata().get("name")).containsExactly("a", "b");
		}
	}

	@Test
	void findByFilter_returnsDeterministicOrderAndTruncatesConsistently() {
		// 内部 store 以随机 UUID 为 key；按条件查列时顺序必须固定，否则拼出的 schema / prompt 每次都不同
		for (int run = 0; run < 5; run++) {
			MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
			store.add(List.of(new Document("c3", Map.of("agentId", "1", "tableName", "t2", "name", "b")),
					new Document("c1", Map.of("agentId", "1", "tableName", "t1", "name", "b")),
					new Document("c2", Map.of("agentId", "1", "tableName", "t1", "name", "a")),
					new Document("c4", Map.of("agentId", "2", "tableName", "t0", "name", "a"))));
			var filter = new FilterExpressionBuilder().eq("agentId", "1").build();
			assertThat(store.findByFilter(filter, 2)).extracting(Document::getText).containsExactly("c2", "c1");
		}
	}

	@Test
	void exactMetadataRetrievalDoesNotRequireAQueryEmbedding() {
		MetadataAwareSimpleVectorStore store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		store.add(List.of(new Document("first order", Map.of("agentId", "1")),
				new Document("second order", Map.of("agentId", "1")),
				new Document("other order", Map.of("agentId", "2"))));
		var filter = new FilterExpressionBuilder().eq("agentId", "1").build();
		MetadataDocumentRetriever retriever = new MetadataDocumentRetriever(new StandardEnvironment());

		assertThat(retriever.find(store, filter, 1)).hasSize(1)
			.allSatisfy(document -> assertThat(document.getMetadata()).containsEntry("agentId", "1"));
	}

	@Test
	void unsupportedStoresFailInsteadOfFallingBackToSyntheticSimilaritySearch() {
		SimpleVectorStore store = SimpleVectorStore.builder(new KeywordEmbeddingModel()).build();
		var filter = new FilterExpressionBuilder().eq("agentId", "1").build();
		MetadataDocumentRetriever retriever = new MetadataDocumentRetriever(new StandardEnvironment());

		assertThatThrownBy(() -> retriever.find(store, filter, 1)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Exact metadata retrieval is not supported");
	}

}
