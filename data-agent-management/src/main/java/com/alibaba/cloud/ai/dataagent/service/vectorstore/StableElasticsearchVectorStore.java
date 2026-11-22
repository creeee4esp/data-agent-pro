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

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchAiSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;

/**
 * 在 Spring AI 的 {@link ElasticsearchVectorStore} 上改写向量检索，解决三个问题：
 * <ol>
 * <li><b>候选数太小</b>：父类固定 {@code num_candidates = 1.5 × topK}，过滤条件多时近似检索召回不足。这里多取一些
 * （{@code k = 2 × topK}，{@code num_candidates = max(100, 4k)}，上限 10,000）；</li>
 * <li><b>分数不精确</b>：ES 8 对 ≥384 维的向量默认用 {@code int8_hnsw}（int8
 * 量化），返回的分数是近似值，且随分段而变，导致同一题每次结果不同。这里建索引时改用 {@code hnsw}（float 向量，分数精确）。代价是向量内存约为量化的 4
 * 倍；</li>
 * <li><b>打平时顺序不确定</b>：分数相同的文档（如结构相同的分表）先后顺序每次可能不同。这里取回后按"分数降序 → 文本 → metadata →
 * id"排序，再截断到 topK（与 {@link MetadataAwareSimpleVectorStore} 的规则一致）。</li>
 * </ol>
 * 只支持余弦相似度（项目默认配置），其余能力（写入、删除、建索引）沿用父类。
 */
public class StableElasticsearchVectorStore extends ElasticsearchVectorStore {

	/** ES 单次 kNN 的 k / num_candidates 上限。 */
	static final int MAX_CANDIDATES = 10_000;

	private static final Comparator<Document> ORDER = Comparator
		.comparing((Document d) -> d.getScore() == null ? 0.0 : d.getScore(), Comparator.reverseOrder())
		.thenComparing(d -> String.valueOf(d.getText()))
		.thenComparing(d -> new TreeMap<>(d.getMetadata()).toString())
		.thenComparing(Document::getId);

	private final ElasticsearchVectorStoreOptions options;

	private final EmbeddingModel embeddingModel;

	private final boolean initializeSchema;

	private final ElasticsearchAiSearchFilterExpressionConverter filterConverter = new ElasticsearchAiSearchFilterExpressionConverter();

	/**
	 * @param initializeSchema 索引不存在时是否创建（与 builder 上的设置保持一致）
	 */
	public StableElasticsearchVectorStore(Builder builder, ElasticsearchVectorStoreOptions options,
			EmbeddingModel embeddingModel, boolean initializeSchema) {
		super(builder.options(options).initializeSchema(initializeSchema));
		this.initializeSchema = initializeSchema;
		if (options.getSimilarity() != SimilarityFunction.cosine) {
			throw new IllegalArgumentException("Only cosine similarity is supported, got " + options.getSimilarity());
		}
		this.options = options;
		this.embeddingModel = embeddingModel;
	}

	/** 索引不存在时先按自己的 mapping 建索引（float hnsw），之后父类检查到索引已存在就不再建。 */
	@Override
	public void afterPropertiesSet() {
		try {
			boolean exists = client().indices().exists(e -> e.index(options.getIndexName())).value();
			if (!exists && initializeSchema) {
				String mapping = "{\"mappings\":{\"properties\":{\"" + options.getEmbeddingFieldName()
						+ "\":{\"type\":\"dense_vector\",\"dims\":" + options.getDimensions()
						+ ",\"index\":true,\"similarity\":\"cosine\",\"index_options\":{\"type\":\"hnsw\"}}}}}";
				client().indices().create(c -> c.index(options.getIndexName()).withJson(new StringReader(mapping)));
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot create Elasticsearch index " + options.getIndexName(), e);
		}
		super.afterPropertiesSet();
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest request) {
		int topK = request.getTopK();
		int k = Math.min(MAX_CANDIDATES, topK * 2);
		int numCandidates = Math.min(MAX_CANDIDATES, Math.max(100, k * 4));
		float threshold = (float) request.getSimilarityThreshold();
		float[] vector = embeddingModel.embed(request.getQuery());
		List<Float> queryVector = new ArrayList<>(vector.length);
		for (float v : vector) {
			queryVector.add(v);
		}
		String filter = request.getFilterExpression() == null ? "*"
				: filterConverter.convertExpression(request.getFilterExpression());
		try {
			SearchResponse<Document> response = client().search(s -> s.index(options.getIndexName())
				.knn(knn -> knn.field(options.getEmbeddingFieldName())
					.queryVector(queryVector)
					.k(k)
					.numCandidates(numCandidates)
					.similarity(threshold)
					.filter(f -> f.queryString(qs -> qs.query(filter))))
				.size(k), Document.class);
			List<Document> documents = new ArrayList<>();
			for (Hit<Document> hit : response.hits().hits()) {
				Document doc = toDocument(hit);
				if (doc != null) {
					documents.add(doc);
				}
			}
			documents.sort(ORDER);
			return documents.size() > topK ? new ArrayList<>(documents.subList(0, topK)) : documents;
		}
		catch (IOException e) {
			throw new IllegalStateException("Elasticsearch similarity search failed", e);
		}
	}

	/** 余弦：ES 的 _score = (1 + cos) / 2，换算回 cos（与父类一致）。 */
	private static Document toDocument(Hit<Document> hit) {
		Document source = hit.source();
		if (source == null) {
			return null;
		}
		Document.Builder builder = source.mutate();
		if (hit.score() != null) {
			double cosine = 2 * hit.score() - 1;
			builder.metadata(DocumentMetadata.DISTANCE.value(), 1 - cosine);
			builder.score(cosine);
		}
		return builder.build();
	}

	private ElasticsearchClient client() {
		return Objects.requireNonNull(this.<ElasticsearchClient>getNativeClient().orElse(null),
				"Elasticsearch native client is unavailable");
	}

}
