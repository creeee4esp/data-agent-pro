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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.service.vectorstore.StableElasticsearchVectorStore;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.springframework.ai.vectorstore.elasticsearch.autoconfigure.ElasticsearchVectorStoreProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * {@code spring.ai.vectorstore.type=elasticsearch} 时注册
 * {@link StableElasticsearchVectorStore}（float hnsw、放大候选数、打平时确定性排序）。
 * <p>
 * Spring AI 的自动配置在已有 {@link ElasticsearchVectorStore} Bean 时不再创建自己的实例；配置项沿用
 * {@code spring.ai.vectorstore.elasticsearch.*}。
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.vectorstore.type", havingValue = "elasticsearch")
public class ElasticsearchVectorStoreConfiguration {

	@Bean
	public ElasticsearchVectorStore vectorStore(ElasticsearchVectorStoreProperties properties, RestClient restClient,
			EmbeddingModel embeddingModel, BatchingStrategy batchingStrategy) {
		ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
		if (StringUtils.hasText(properties.getIndexName())) {
			options.setIndexName(properties.getIndexName());
		}
		if (properties.getDimensions() != null) {
			options.setDimensions(properties.getDimensions());
		}
		if (StringUtils.hasText(properties.getEmbeddingFieldName())) {
			options.setEmbeddingFieldName(properties.getEmbeddingFieldName());
		}
		options.setSimilarity(SimilarityFunction.cosine);
		return new StableElasticsearchVectorStore(
				ElasticsearchVectorStore.builder(restClient, embeddingModel).batchingStrategy(batchingStrategy),
				options, embeddingModel, properties.isInitializeSchema());
	}

}
