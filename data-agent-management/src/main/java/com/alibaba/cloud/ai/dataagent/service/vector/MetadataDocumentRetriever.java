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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 向量库扩展能力的入口：按向量库类型选择对应的 {@link VectorStoreExtension}。
 * <p>
 * 原来这里用 instanceof 分支直接实现各家的查询；现在每种向量库一个实现类，新增向量库只需在构造器里注册一个实现。 类名沿用
 * MetadataDocumentRetriever，调用方不用改。
 *
 * 现在这个类不止负责召回，还可以修改metadata
 */
@Component
public class MetadataDocumentRetriever {

	private final List<VectorStoreExtension> extensions; // 所有已注册的实现

	public MetadataDocumentRetriever(Environment environment) {
		this.extensions = Arrays.asList(new SimpleVectorStoreExtension(), new ElasticsearchVectorStoreExtension(),
				new MilvusVectorStoreExtension(environment));
	}

	/** 按 metadata 条件精确查询。 */
	public List<Document> find(VectorStore vectorStore, Filter.Expression filterExpression, int limit) {
		return extensionFor(vectorStore).findByFilter(vectorStore, filterExpression, limit);
	}

	/** 该向量库是否支持只改 metadata。 */
	public boolean supportsMetadataUpdate(VectorStore vectorStore) {
		for (VectorStoreExtension extension : extensions) {
			if (extension.supports(vectorStore)) {
				return extension.supportsMetadataUpdate();
			}
		}
		return false;
	}

	/** 按条件批量更新 metadata，返回更新的文档数。 */
	public int updateMetadata(VectorStore vectorStore, Filter.Expression filterExpression,
			Map<String, Object> changes) {
		return extensionFor(vectorStore).updateMetadata(vectorStore, filterExpression, changes);
	}

	/**
	 * 选出对应的实现
	 * @param vectorStore
	 * @return
	 */
	private VectorStoreExtension extensionFor(VectorStore vectorStore) {
		for (VectorStoreExtension extension : extensions) {
			if (extension.supports(vectorStore)) {
				return extension;
			}
		}
		throw new IllegalArgumentException(
				"Exact metadata retrieval is not supported for " + vectorStore.getClass().getName());
	}

}
