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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonData;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchAiSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/** Elasticsearch：用原生客户端做按条件查询和 update_by_query。 */
class ElasticsearchVectorStoreExtension implements VectorStoreExtension {

	/** 只改 metadata 里给定的键：ctx._source.metadata[key] = value。 */
	private static final String UPDATE_SCRIPT = "for (entry in params.changes.entrySet()) "
			+ "{ ctx._source.metadata[entry.getKey()] = entry.getValue(); }";

	@Override
	public boolean supports(VectorStore vectorStore) {
		return vectorStore instanceof ElasticsearchVectorStore;
	}

	/**
	 * 按条件精确查询，结果按 {@link DocumentFilterOrder#ORDER} 排序。
	 * <p>
	 * ES 的过滤查询没有相似度参与，命中文档的先后取决于索引内部结构，同一批数据可能每次不同。这里在返回前统一排序， 与内存向量库的
	 * {@code findByFilter} 保持一致，让拼出的 schema / prompt 可复现。
	 * <p>
	 * 注意：{@code size(limit)} 在排序之前执行。调用方按"覆盖全部匹配"设置 limit 时结果与内存实现完全一致； 匹配数超过 limit 时，先取哪
	 * limit 条仍由 ES 决定。
	 * @param vectorStore Elasticsearch 向量库
	 * @param filter metadata 过滤条件
	 * @param limit 最多返回条数
	 * @return 排好序的文档
	 */
	@Override
	public List<Document> findByFilter(VectorStore vectorStore, Filter.Expression filter, int limit) {
		ElasticsearchVectorStore store = (ElasticsearchVectorStore) vectorStore;
		String query = new ElasticsearchAiSearchFilterExpressionConverter().convertExpression(filter);
		try {
			return client(store)
				.search(search -> search.index(indexName(store))
					.query(q -> q.queryString(qs -> qs.query(query)))
					.size(limit), Document.class)
				.hits()
				.hits()
				.stream()
				.map(hit -> hit.source())
				.filter(Objects::nonNull)
				.sorted(DocumentFilterOrder.ORDER)
				.limit(limit)
				.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Elasticsearch metadata query failed", ex);
		}
	}

	@Override
	public boolean supportsMetadataUpdate() {
		return true;
	}

	@Override
	public int updateMetadata(VectorStore vectorStore, Filter.Expression filter, Map<String, Object> changes) {
		ElasticsearchVectorStore store = (ElasticsearchVectorStore) vectorStore;
		String query = new ElasticsearchAiSearchFilterExpressionConverter().convertExpression(filter);
		Map<String, JsonData> params = new HashMap<>();
		params.put("changes", JsonData.of(changes));
		try {
			Long updated = client(store)
				.updateByQuery(u -> u.index(indexName(store))
					.query(q -> q.queryString(qs -> qs.query(query)))
					.script(s -> s.source(UPDATE_SCRIPT).params(params))
					// 立即刷新：开关改完后下一次检索就能看到
					.refresh(true))
				.updated();
			return updated == null ? 0 : updated.intValue();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Elasticsearch metadata update failed", ex);
		}
	}

	private static ElasticsearchClient client(ElasticsearchVectorStore store) {
		return store.<ElasticsearchClient>getNativeClient()
			.orElseThrow(() -> new IllegalStateException("Elasticsearch native client is unavailable"));
	}

	private static String indexName(ElasticsearchVectorStore store) {
		return store.createObservationContextBuilder("metadata-query").build().getCollectionName();
	}

}
