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

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/**
 * Spring AI的VectorStore只提供三类接口: 写入(add)、删除(delete)、向量检索(similaritySearch) 当前项目还需要:
 * 1.按metadata精确查询(不计算查询向量) 场景: 按表名取列、替换文档前先查出旧文档 2.只改metadata(不改文本和向量) 场景: 召回开关的切换
 */
public interface VectorStoreExtension {

	/** 是否适用于这个向量库实例。 */
	boolean supports(VectorStore vectorStore);

	/** 按 metadata 条件精确查询（不计算查询向量），最多返回 {@code limit} 条。 */
	List<Document> findByFilter(VectorStore vectorStore, Filter.Expression filter, int limit);

	/** 是否支持 {@link #updateMetadata}。 */
	default boolean supportsMetadataUpdate() {
		return false;
	}

	/**
	 * 按条件批量更新 metadata：只覆盖 {@code changes} 里的键，不改文本和向量。
	 * @return 更新的文档数
	 * @throws UnsupportedOperationException 该向量库不支持
	 */
	default int updateMetadata(VectorStore vectorStore, Filter.Expression filter, Map<String, Object> changes) {
		throw new UnsupportedOperationException(
				"Metadata update is not supported for " + vectorStore.getClass().getName());
	}

}
