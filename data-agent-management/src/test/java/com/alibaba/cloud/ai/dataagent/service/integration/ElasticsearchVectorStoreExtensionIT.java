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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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
import com.alibaba.cloud.ai.dataagent.service.vectorstore.StableElasticsearchVectorStore;
import com.alibaba.cloud.ai.dataagent.support.KeywordEmbeddingModel;
import com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.env.StandardEnvironment;

/**
 * 在真实 Elasticsearch 上验证 {@code ElasticsearchVectorStoreExtension}：按条件精确查询、update_by_query
 * 只改 metadata、 召回开关过滤（{@code recall != false}，缺失按召回处理）。
 * <p>
 * 需要本机 ES（默认 http://127.0.0.1:9200，可用 {@code -Des.url=} 覆盖）。不在默认的 {@code mvn test} 里运行：
 *
 * <pre>
 * .\mvnw.cmd -pl data-agent-management test "-Dtest=ElasticsearchVectorStoreExtensionIT" "-Djacoco.skip=true"
 * </pre>
 */
class ElasticsearchVectorStoreExtensionIT {

	private RestClient restClient;

	private String index;

	private ElasticsearchVectorStore store;

	private AgentVectorStoreService service;

	@BeforeEach
	void setUp() throws Exception {
		restClient = RestClient.builder(HttpHost.create(System.getProperty("es.url", "http://127.0.0.1:9200"))).build();
		index = "it-recall-" + System.currentTimeMillis();
		ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
		options.setIndexName(index);
		options.setDimensions(3);
		options.setSimilarity(SimilarityFunction.cosine);
		KeywordEmbeddingModel embeddingModel = new KeywordEmbeddingModel();
		store = new StableElasticsearchVectorStore(ElasticsearchVectorStore.builder(restClient, embeddingModel),
				options, embeddingModel, true);
		store.afterPropertiesSet();
		// DynamicFilterService 的 mapper 传 null：ES 支持只改 metadata，检索不应再查 MySQL
		service = new AgentVectorStoreServiceImpl(store, Optional.empty(), new DataAgentProperties(),
				new DynamicFilterService(null, null), new MetadataDocumentRetriever(new StandardEnvironment()));

		service.addDocuments("1", List.of(DocumentConverterUtil.convertBusinessKnowledgeToDocument(term(10L, "订单"))));
		// 加 recall 字段之前写入的旧文档
		Map<String, Object> legacy = new HashMap<>();
		legacy.put(Constant.AGENT_ID, "1");
		legacy.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.BUSINESS_TERM);
		legacy.put(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, 11L);
		service.addDocuments("1", List.of(new Document("业务名词: 订单金额", legacy)));
		refresh();
	}

	@AfterEach
	void tearDown() throws Exception {
		restClient.performRequest(new Request("DELETE", "/" + index));
		restClient.close();
	}

	@Test
	void indexUsesFloatHnswForExactScores() throws Exception {
		String mapping = new String(restClient.performRequest(new Request("GET", "/" + index + "/_mapping"))
			.getEntity()
			.getContent()
			.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(mapping).contains("\"type\":\"hnsw\"").doesNotContain("int8_hnsw");
	}

	@Test
	void supportsMetadataUpdateAndExactFilterQuery() {
		assertThat(service.supportsMetadataUpdate()).isTrue();
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Document> found = service
			.getDocumentsOnlyByFilter(b.eq(DocumentMetadataConstant.DB_BUSINESS_TERM_ID, 10L).build(), 10);
		assertThat(found).hasSize(1);
		assertThat(found.get(0).getMetadata()).containsEntry(DocumentMetadataConstant.RECALL, true);
	}

	@Test
	void filterQueryReturnsDeterministicOrder() throws Exception {
		Map<String, Object> base = new HashMap<>();
		base.put(Constant.AGENT_ID, "2");
		base.put(DocumentMetadataConstant.VECTOR_TYPE, "filter-order-test");
		// 按期望顺序的逆序、分 3 次写入（每次 refresh 产生一个新分段）：ES 自然命中顺序与期望顺序不同，
		// 去掉 ElasticsearchVectorStoreExtension 里的排序，这个用例就会失败
		List<String> expected = new ArrayList<>();
		for (int t = 1; t <= 3; t++) {
			for (int n = 1; n <= 4; n++) {
				expected.add("t" + t + ".c" + n);
			}
		}
		List<String> reversed = new ArrayList<>(expected);
		Collections.reverse(reversed);
		for (int start = 0; start < reversed.size(); start += 4) {
			List<Document> batch = new ArrayList<>();
			for (String text : reversed.subList(start, start + 4)) {
				String[] parts = text.split("\\.");
				batch.add(document(text, parts[0], parts[1], base));
			}
			service.addDocuments("2", batch);
			refresh();
		}

		String raw = new String(restClient
			.performRequest(
					new Request("GET", "/" + index + "/_search?size=100&q=metadata.vectorType:filter-order-test"))
			.getEntity()
			.getContent()
			.readAllBytes(), StandardCharsets.UTF_8);
		assertThat(raw.indexOf("t3.c4")).as("precondition: ES natural order differs from expected order")
			.isLessThan(raw.indexOf("t1.c1"));

		FilterExpressionBuilder b = new FilterExpressionBuilder();
		for (int run = 0; run < 3; run++) {
			List<Document> found = service.getDocumentsOnlyByFilter(
					b.and(b.eq(Constant.AGENT_ID, "2"), b.eq(DocumentMetadataConstant.VECTOR_TYPE, "filter-order-test"))
						.build(),
					100);
			assertThat(found).extracting(Document::getText).containsExactlyElementsOf(expected);
		}
	}

	@Test
	void legacyDocumentIsRecalledOnlyAfterSync() {
		assertThat(search()).hasSize(1);
		service.updateMetadata(match(11L), Map.of(DocumentMetadataConstant.RECALL, true));
		assertThat(search()).hasSize(2);
	}

	@Test
	void toggleOffThenOnChangesSearchResultsWithoutReEmbedding() throws Exception {
		service.updateMetadata(match(11L), Map.of(DocumentMetadataConstant.RECALL, true));
		assertThat(search()).hasSize(2);

		int updated = service.updateMetadata(match(10L), Map.of(DocumentMetadataConstant.RECALL, false));
		assertThat(updated).isEqualTo(1);
		assertThat(search())
			.extracting(d -> String.valueOf(d.getMetadata().get(DocumentMetadataConstant.DB_BUSINESS_TERM_ID)))
			.containsExactly("11");

		service.updateMetadata(match(10L), Map.of(DocumentMetadataConstant.RECALL, true));
		assertThat(search()).hasSize(2);
	}

	private List<Document> search() {
		return service.getDocumentsForAgent("1", "订单", DocumentMetadataConstant.BUSINESS_TERM, 10, 0.0);
	}

	private void refresh() throws Exception {
		restClient.performRequest(new Request("POST", "/" + index + "/_refresh"));
	}

	private static Document document(String text, String tableName, String name, Map<String, Object> base) {
		Map<String, Object> metadata = new HashMap<>(base);
		metadata.put("tableName", tableName);
		metadata.put("name", name);
		return new Document(text, metadata);
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
