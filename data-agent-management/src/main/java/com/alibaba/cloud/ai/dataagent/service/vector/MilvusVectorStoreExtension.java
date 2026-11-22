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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.QueryResults;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.response.QueryResultsWrapper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.core.env.Environment;

/**
 * Milvus：按条件查询（原 MetadataDocumentRetriever 中的实现原样迁移）。不支持只改 metadata（Milvus 的 upsert
 * 需要带上向量）， 召回开关继续走查 MySQL 的旧方式。
 */
class MilvusVectorStoreExtension implements VectorStoreExtension {

	private final Environment environment;

	MilvusVectorStoreExtension(Environment environment) {
		this.environment = environment;
	}

	@Override
	public boolean supports(VectorStore vectorStore) {
		return vectorStore instanceof MilvusVectorStore;
	}

	@Override
	public List<Document> findByFilter(VectorStore vectorStore, Filter.Expression filterExpression, int limit) {
		MilvusVectorStore milvus = (MilvusVectorStore) vectorStore;
		VectorStoreObservationContext context = milvus.createObservationContextBuilder("metadata-query").build();
		String idField = environment.getProperty("spring.ai.vectorstore.milvus.id-field-name",
				MilvusVectorStore.DOC_ID_FIELD_NAME);
		String contentField = environment.getProperty("spring.ai.vectorstore.milvus.content-field-name",
				MilvusVectorStore.CONTENT_FIELD_NAME);
		String metadataField = environment.getProperty("spring.ai.vectorstore.milvus.metadata-field-name",
				MilvusVectorStore.METADATA_FIELD_NAME);
		MilvusServiceClient client = milvus.<MilvusServiceClient>getNativeClient()
			.orElseThrow(() -> new IllegalStateException("Milvus native client is unavailable"));
		QueryParam query = QueryParam.newBuilder()
			.withDatabaseName(context.getNamespace())
			.withCollectionName(context.getCollectionName())
			.withConsistencyLevel(ConsistencyLevelEnum.STRONG)
			.withOutFields(List.of(idField, contentField, metadataField))
			.withExpr(milvus.filterExpressionConverter.convertExpression(filterExpression))
			.withLimit((long) limit)
			.build();
		R<QueryResults> response = client.query(query);
		if (response.getException() != null) {
			throw new IllegalStateException("Milvus metadata query failed", response.getException());
		}
		Gson gson = new Gson();
		Type metadataType = new TypeToken<Map<String, Object>>() {
		}.getType();
		return new QueryResultsWrapper(response.getData()).getRowRecords().stream().map(row -> {
			JsonObject metadata = (JsonObject) row.get(metadataField);
			return Document.builder()
				.id(String.valueOf(row.get(idField)))
				.text((String) row.get(contentField))
				.metadata(metadata == null ? Map.of() : gson.fromJson(metadata, metadataType))
				.build();
		}).toList();
	}

}
