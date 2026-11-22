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

import com.alibaba.cloud.ai.dataagent.service.vectorstore.MetadataAwareSimpleVectorStore;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/** 进程内存向量库：直接操作内存中的文档。 */
class SimpleVectorStoreExtension implements VectorStoreExtension {

	@Override
	public boolean supports(VectorStore vectorStore) {
		return vectorStore instanceof MetadataAwareSimpleVectorStore;
	}

	@Override
	public List<Document> findByFilter(VectorStore vectorStore, Filter.Expression filter, int limit) {
		return ((MetadataAwareSimpleVectorStore) vectorStore).findByFilter(filter, limit);
	}

	@Override
	public boolean supportsMetadataUpdate() {
		return true;
	}

	@Override
	public int updateMetadata(VectorStore vectorStore, Filter.Expression filter, Map<String, Object> changes) {
		return ((MetadataAwareSimpleVectorStore) vectorStore).updateMetadata(filter, changes);
	}

}
