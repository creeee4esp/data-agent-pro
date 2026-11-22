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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;

@ExtendWith(MockitoExtension.class)
class AgentKnowledgeRetrieverTest {

	private static final double THRESHOLD = new DataAgentProperties().getVectorStore().getDefaultSimilarityThreshold();

	@Mock
	private AgentVectorStoreService store;

	private AgentKnowledgeRetriever retriever() {
		return new AgentKnowledgeRetriever(store, new DataAgentProperties());
	}

	private static Document qa(String id, double score) {
		Map<String, Object> m = new HashMap<>();
		m.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, 9);
		return Document.builder().id(id).text("问题 " + id).metadata(m).score(score).build();
	}

	private static Document child(String parentId, int childSeq, String body, double score) {
		Map<String, Object> m = new HashMap<>();
		m.put(DocumentMetadataConstant.PARENT_ID, parentId);
		m.put(DocumentMetadataConstant.CHILD_SEQ, childSeq);
		m.put(DocumentMetadataConstant.SECTION, "文档 > " + parentId);
		return Document.builder()
			.id(parentId + "#" + childSeq)
			.text("【文档 > " + parentId + "】\n" + body)
			.metadata(m)
			.score(score)
			.build();
	}

	@Test
	void withoutChildrenReturnsFirstLegacyTopK() {
		List<Document> hits = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			hits.add(qa("q" + i, 0.9 - i * 0.01));
		}
		when(store.getDocumentsForAgent("1", "问", DocumentMetadataConstant.AGENT_KNOWLEDGE, 30, THRESHOLD))
			.thenReturn(hits);

		List<Document> result = retriever().retrieve("1", "问");

		assertThat(result).hasSize(8).containsExactlyElementsOf(hits.subList(0, 8));
		verify(store, never()).getDocumentsOnlyByFilter(any(), anyInt());
	}

	@Test
	void childrenAreGroupedAndExpandedToParentsInScoreOrder() {
		when(store.getDocumentsForAgent("1", "问", DocumentMetadataConstant.AGENT_KNOWLEDGE, 30, THRESHOLD))
			.thenReturn(Arrays.asList(child("5_1", 1, "B", 0.9), qa("q1", 0.85), child("5_1", 0, "A", 0.8),
					child("7_0", 0, "X", 0.7)));
		// 向量库返回的顺序与 childSeq 无关，拼回时要按 childSeq 排序
		when(store.getDocumentsOnlyByFilter(any(Filter.Expression.class), eq(2000)))
			.thenReturn(Arrays.asList(child("5_1", 1, "B", 0), child("7_0", 0, "X", 0), child("5_1", 0, "A", 0)));

		List<Document> result = retriever().retrieve("1", "问");

		assertThat(result).extracting(Document::getText)
			.containsExactly("【文档 > 5_1】\nA\nB", "问题 q1", "【文档 > 7_0】\nX");
		assertThat(result.get(0).getScore()).isEqualTo(0.9);
	}

	@Test
	void budgetStopsAfterFirstGroupThatOverflows() {
		String longBody = new String(new char[4000]).replace('\0', '长');
		when(store.getDocumentsForAgent("1", "问", DocumentMetadataConstant.AGENT_KNOWLEDGE, 30, THRESHOLD))
			.thenReturn(Arrays.asList(child("1_0", 0, longBody, 0.9), child("2_0", 0, longBody, 0.8), qa("q", 0.7)));
		when(store.getDocumentsOnlyByFilter(any(Filter.Expression.class), eq(2000)))
			.thenReturn(Arrays.asList(child("1_0", 0, longBody, 0), child("2_0", 0, longBody, 0)));

		List<Document> result = retriever().retrieve("1", "问");

		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo("1_0");
	}

	@Test
	void rerankReordersDropsLowScoresAndCapsResults() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getAgentKnowledge().getRerank().setEnabled(true);
		properties.getAgentKnowledge().getRerank().setMaxResults(2);
		KnowledgeReranker reranker = mock(KnowledgeReranker.class);
		when(reranker.isEnabled()).thenReturn(true);
		// 向量顺序 q1, q2, q3, q4；精排后 q3 > q1 > q4，q2 低于 0.5 被丢掉，最多留 2 个
		when(reranker.score(eq("问"), anyList())).thenReturn(new double[] { 0.8, 0.1, 0.95, 0.6 });
		when(store.getDocumentsForAgent("1", "问", DocumentMetadataConstant.AGENT_KNOWLEDGE, 30, THRESHOLD))
			.thenReturn(Arrays.asList(child("1_0", 0, "q1", 0.9), child("2_0", 0, "q2", 0.8),
					child("3_0", 0, "q3", 0.7), child("4_0", 0, "q4", 0.6)));
		when(store.getDocumentsOnlyByFilter(any(Filter.Expression.class), eq(2000)))
			.thenReturn(Arrays.asList(child("1_0", 0, "q1", 0), child("2_0", 0, "q2", 0), child("3_0", 0, "q3", 0),
					child("4_0", 0, "q4", 0)));

		List<Document> result = new AgentKnowledgeRetriever(store, properties, reranker).retrieve("1", "问");

		assertThat(result).extracting(Document::getId).containsExactly("3_0", "1_0");
		assertThat(result.get(0).getMetadata()).containsEntry(DocumentMetadataConstant.RERANK_SCORE, 0.95);
	}

	@Test
	void rerankFailureFallsBackToVectorOrder() {
		DataAgentProperties properties = new DataAgentProperties();
		properties.getAgentKnowledge().getRerank().setEnabled(true);
		KnowledgeReranker reranker = mock(KnowledgeReranker.class);
		when(reranker.isEnabled()).thenReturn(true);
		when(reranker.score(eq("问"), anyList())).thenReturn(null);
		when(store.getDocumentsForAgent("1", "问", DocumentMetadataConstant.AGENT_KNOWLEDGE, 30, THRESHOLD))
			.thenReturn(Arrays.asList(child("1_0", 0, "a", 0.9), child("2_0", 0, "b", 0.8)));
		when(store.getDocumentsOnlyByFilter(any(Filter.Expression.class), eq(2000)))
			.thenReturn(Arrays.asList(child("1_0", 0, "a", 0), child("2_0", 0, "b", 0)));

		List<Document> result = new AgentKnowledgeRetriever(store, properties, reranker).retrieve("1", "问");

		assertThat(result).extracting(Document::getId).containsExactly("1_0", "2_0");
	}

}
