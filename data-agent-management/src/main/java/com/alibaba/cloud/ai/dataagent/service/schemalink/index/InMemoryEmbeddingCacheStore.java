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
package com.alibaba.cloud.ai.dataagent.service.schemalink.index;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现：用 {@link ConcurrentHashMap} 保存，进程退出即丢失。
 * <p>
 * 用途：单元测试（不碰磁盘、互不干扰），以及不需要持久化的短时任务。
 * <p>
 * 存入和取出时都复制数组：float[] 是可变对象，如果直接共享引用，调用方修改返回的数组会悄悄改坏缓存里的数据。
 */
public class InMemoryEmbeddingCacheStore implements EmbeddingCacheStore {

	private final Map<String, float[]> entries = new ConcurrentHashMap<>();

	@Override
	public Map<String, float[]> getAll(Collection<String> keys) {
		Map<String, float[]> hits = new LinkedHashMap<>();
		for (String key : keys) {
			float[] vector = entries.get(key);
			if (vector != null) {
				// 复制一份再返回，防止调用方修改影响缓存内数据
				hits.put(key, vector.clone());
			}
		}
		return hits;
	}

	@Override
	public void putAll(Map<String, float[]> vectors) {
		for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
			entries.put(entry.getKey(), entry.getValue().clone());
		}
	}

	/** 当前缓存条目数，便于测试断言。 */
	public int size() {
		return entries.size();
	}

}
