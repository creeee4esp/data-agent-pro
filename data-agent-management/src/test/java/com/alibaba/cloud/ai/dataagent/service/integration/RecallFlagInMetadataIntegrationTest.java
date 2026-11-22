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
package com.alibaba.cloud.ai.dataagent.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.MetadataAwareSimpleVectorStore;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.env.StandardEnvironment;

/**
 * 召回开关写在文档 metadata 上：关闭后检索不到，重新打开后又能检索到；旧文档（没有 recall 字段）在启动同步补上字段之前检索不到；检索不再依赖 MySQL。
 */
class RecallFlagInMetadataIntegrationTest {

	private MetadataAwareSimpleVectorStore store;

	private AgentVectorStoreService service;

	@BeforeEach
	void setUp() {
		store = new MetadataAwareSimpleVectorStore(new KeywordEmbeddingModel());
		// DynamicFilterService 的两个 mapper 传 null：metadata 模式下检索不能再查 MySQL，查了就会空指针
		service = new AgentVectorStoreServiceImpl(store, Optional.empty(), new DataAgentProperties(),
				new DynamicFilterService(null, null), new MetadataDocumentRetriever(new StandardEnvironment()));
		service.addDocuments("1", List.of(DocumentConverterUtil.convertBusinessKnowledgeToDocument(term(10L, "订单"))));
		// 加 recall 字段之前写入的旧文档
		Map<String, Object> legacy = new HashMap<>();
		legacy.put(Constant.AGENT_ID, "1");
		legacy.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
		legacy.put(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, 11L);
		service.addDocuments("1", List.of(new Document("业务名词: 订单金额", legacy)));
	}

	@Test
	void legacyDocumentWithoutRecallFieldIsExcludedUntilSynced() {
		assertThat(search()).hasSize(1);
		service.updateMetadata(match(11L), Map.of(DocumentMetadataConstant.RECALL, true));
		assertThat(search()).hasSize(2);
	}

	@Test
	void toggledOffKnowledgeIsExcluded() {
		assertThat(service.supportsMetadataUpdate()).isTrue();
		service.updateMetadata(match(11L), Map.of(DocumentMetadataConstant.RECALL, true));
		assertThat(search()).hasSize(2);

		int updated = service.updateMetadata(match(10L), Map.of(DocumentMetadataConstant.RECALL, false));

		assertThat(updated).isEqualTo(1);
		assertThat(search()).extracting(d -> d.getMetadata().get(DocumentMetadataConstant.DB_BUSINESS_TERM_ID))
			.containsExactly(11L);
	}

	@Test
	void toggledBackOnKnowledgeIsRecalledAgainWithoutReEmbedding() {
		service.updateMetadata(match(11L), Map.of(DocumentMetadataConstant.RECALL, true));
		service.updateMetadata(match(10L), Map.of(DocumentMetadataConstant.RECALL, false));
		service.updateMetadata(match(10L), Map.of(DocumentMetadataConstant.RECALL, true));

		assertThat(search()).hasSize(2);
	}

	private List<Document> search() {
		return service.getDocumentsForAgent("1", "订单", DocumentMetadataConstant.BUSINESS_TERM, 10, 0.0);
	}

	private static Map<String, Object> match(Long id) {
		return Map.of(Constant.AGENT_ID, "1", DocumentMetadataConstant.VECTOR_TYPE,
				DocumentMetadataConstant.BUSINESS_TERM, DocumentMetadataConstant.DB_BUSINESS_TERM_ID, id);
	}

	private static BusinessKnowledge term(Long id, String name) {
		BusinessKnowledge k = new BusinessKnowledge();
		k.setId(id);
		k.setAgentId(1L);
		k.setBusinessTerm(name);
		k.setDescription(name + "的定义");
		k.setIsRecall(1);
		return k;
	}

}
