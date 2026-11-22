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
package com.alibaba.cloud.ai.dataagent.service.schemalink.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InMemoryEmbeddingCacheStoreTest {

	@Test
	void mutatingReturnedOrStoredArray_doesNotCorruptCache() {
		InMemoryEmbeddingCacheStore store = new InMemoryEmbeddingCacheStore();
		float[] original = { 1f, 2f };
		store.putAll(Collections.singletonMap("k", original));

		original[0] = 100f; // 修改存入时传入的数组
		float[] read = store.getAll(Collections.singletonList("k")).get("k");
		read[1] = 200f; // 修改读出的数组

		assertArrayEquals(new float[] { 1f, 2f }, store.getAll(Collections.singletonList("k")).get("k"));
	}

	@Test
	void getAll_missingKeys_areAbsent() {
		InMemoryEmbeddingCacheStore store = new InMemoryEmbeddingCacheStore();
		store.putAll(Collections.singletonMap("k", new float[] { 1f }));

		Map<String, float[]> hits = store.getAll(Arrays.asList("k", "missing"));

		assertEquals(Collections.singleton("k"), hits.keySet());
		assertEquals(1, store.size());
	}

}
