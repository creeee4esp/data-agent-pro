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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.service.vector.DocumentFilterOrder;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStoreContent;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.SimpleVectorStoreFilterExpressionConverter;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Adds metadata deletion to Spring AI's in-memory fallback store.
 *
 * <p>
 * {@link SimpleVectorStore} supports ID deletion but not filter deletion. Selecting IDs
 * through similarity search would invoke the embedding model for a delete operation. This
 * adapter keeps Spring AI's storage and lifecycle implementation while supplying the one
 * provider-specific operation that it does not expose.
 */
public final class MetadataAwareSimpleVectorStore extends SimpleVectorStore {

	private final SpelExpressionParser expressionParser = new SpelExpressionParser();

	private final SimpleVectorStoreFilterExpressionConverter filterConverter = new SimpleVectorStoreFilterExpressionConverter();

	public MetadataAwareSimpleVectorStore(EmbeddingModel embeddingModel) {
		super(SimpleVectorStore.builder(embeddingModel));
	}

	/**
	 * 相似度检索，相似度相同时按确定的顺序排序。
	 * <p>
	 * 父类把文档存在以随机 UUID 为 key 的 ConcurrentHashMap 中，再按相似度做稳定排序；相似度相同的文档（例如内容完全相同的
	 * 备份表文档）的先后顺序取决于 Map 的遍历顺序，每次运行都可能不同，topK 截断时会随机丢掉其中一部分，召回结果不可复现。
	 * 这里先取出全部达到阈值的结果，再按"相似度降序 → 文本 → metadata → id"排序后截断，只改变打平时的顺序。
	 */
	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		SearchRequest all = SearchRequest.from(request).topK(Math.max(request.getTopK(), this.store.size())).build();
		List<Document> candidates = new ArrayList<>(super.doSimilaritySearch(all));
		candidates.sort(TIE_BREAK);
		return candidates.size() > request.getTopK() ? new ArrayList<>(candidates.subList(0, request.getTopK()))
				: candidates;
	}

	private static final Comparator<Document> TIE_BREAK = Comparator
		.comparing((Document d) -> d.getScore() == null ? 0.0 : d.getScore(), Comparator.reverseOrder())
		.thenComparing(d -> String.valueOf(d.getText()))
		.thenComparing(d -> new TreeMap<>(d.getMetadata()).toString())
		.thenComparing(Document::getId);

	/**
	 * 按条件批量更新 metadata：只覆盖 {@code changes} 里的键，文本和向量不变。
	 * <p>
	 * 父类的 {@link SimpleVectorStoreContent} 不可变，这里用合并后的 metadata 新建一个对象替换原条目，不需要重新计算向量。
	 * @return 更新的文档数
	 */
	public int updateMetadata(Filter.Expression filterExpression, Map<String, Object> changes) {
		var expression = expressionParser.parseExpression(filterConverter.convertExpression(filterExpression));
		int updated = 0;
		for (SimpleVectorStoreContent content : new ArrayList<>(store.values())) {
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setVariable("metadata", content.getMetadata());
			if (Boolean.TRUE.equals(expression.getValue(context, Boolean.class))) {
				Map<String, Object> merged = new HashMap<>(content.getMetadata());
				merged.putAll(changes);
				store.put(content.getId(), new SimpleVectorStoreContent(content.getId(), content.getText(), merged,
						content.getEmbedding()));
				updated++;
			}
		}
		return updated;
	}

	public int deleteByMetadata(Map<String, Object> metadata) {
		List<String> ids = this.store.entrySet()
			.stream()
			.filter(entry -> matches(entry.getValue().getMetadata(), metadata))
			.map(Map.Entry::getKey)
			.toList();
		doDelete(ids);
		return ids.size();
	}

	/**
	 * 按 metadata 条件查文档（不做相似度检索），结果顺序确定。
	 * <p>
	 * 父类的 store 以随机 UUID 为 key，直接遍历时顺序每次初始化都不同；按表取列时，这会让拼出来的 schema 文本（以及 prompt）每次都不一样。
	 * 这里在截断前按 {@link DocumentFilterOrder#ORDER} 排序。
	 */
	public List<Document> findByFilter(Filter.Expression filterExpression, int limit) {
		var expression = expressionParser.parseExpression(filterConverter.convertExpression(filterExpression));
		return store.values().stream().filter(content -> {
			StandardEvaluationContext context = new StandardEvaluationContext();
			context.setVariable("metadata", content.getMetadata());
			return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
		})
			.map(content -> Document.builder()
				.id(content.getId())
				.text(content.getText())
				.metadata(content.getMetadata())
				.build())
			.sorted(DocumentFilterOrder.ORDER)
			.limit(limit)
			.toList();
	}

	private boolean matches(Map<String, Object> documentMetadata, Map<String, Object> expectedMetadata) {
		return expectedMetadata.entrySet()
			.stream()
			.allMatch(entry -> valuesEqual(documentMetadata.get(entry.getKey()), entry.getValue()));
	}

	private boolean valuesEqual(Object actual, Object expected) {
		return actual != null && expected != null && actual.toString().equals(expected.toString());
	}

}
