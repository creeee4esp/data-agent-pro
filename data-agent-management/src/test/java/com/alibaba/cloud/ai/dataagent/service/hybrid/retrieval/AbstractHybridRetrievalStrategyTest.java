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
package com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval;

import com.alibaba.cloud.ai.dataagent.dto.search.HybridSearchRequest;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.FusionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractHybridRetrievalStrategyTest {

	@Mock
	private VectorStore vectorStore;

	@Mock
	private FusionStrategy fusionStrategy;

	private ExecutorService executorService;

	private TestHybridRetrievalStrategy strategy;

	@BeforeEach
	void setUp() {
		executorService = Executors.newFixedThreadPool(2);
		strategy = new TestHybridRetrievalStrategy(executorService, vectorStore, fusionStrategy);
	}

	@Test
	void testRetrieve_combinesVectorAndKeywordResults() {
		Document vectorDoc = new Document("vector result");
		Document keywordDoc = new Document("keyword result");
		Document fusedDoc = new Document("fused result");

		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(vectorDoc));
		when(fusionStrategy.fuseResults(anyInt(), any(), any())).thenReturn(List.of(fusedDoc));

		strategy.setKeywordResults(List.of(keywordDoc));

		HybridSearchRequest request = HybridSearchRequest.builder().query("test").topK(5).build();

		List<Document> results = strategy.retrieve(request);
		assertNotNull(results);
		assertEquals(1, results.size());
		assertEquals("fused result", results.get(0).getText());
	}

	@Test
	void testRetrieve_emptyResults() {
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

		strategy.setKeywordResults(List.of());

		HybridSearchRequest request = HybridSearchRequest.builder().query("test").topK(5).build();

		List<Document> results = strategy.retrieve(request);
		assertNotNull(results);
		assertTrue(results.isEmpty());
	}

	@Test
	void testRetrieve_noVectorResultAboveThreshold_ignoresKeywordResults() {
		// 向量门控：无关查询在向量一路被阈值挡掉时，关键词一路即使匹配到单字也不返回
		when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
		strategy.setKeywordResults(List.of(new Document("keyword only")));

		List<Document> results = strategy.retrieve(HybridSearchRequest.builder().query("年假怎么申请").topK(5).build());

		assertTrue(results.isEmpty());
		verify(fusionStrategy, never()).fuseResults(anyInt(), any(), any());
	}

	static class TestHybridRetrievalStrategy extends AbstractHybridRetrievalStrategy {

		private List<Document> keywordResults = List.of();

		TestHybridRetrievalStrategy(ExecutorService executorService, VectorStore vectorStore,
				FusionStrategy fusionStrategy) {
			super(executorService, vectorStore, fusionStrategy);
		}

		void setKeywordResults(List<Document> results) {
			this.keywordResults = results;
		}

		@Override
		public List<Document> getDocumentsByKeywords(HybridSearchRequest request) {
			return keywordResults;
		}

	}

}
