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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileEmbeddingCacheStoreTest {

	/** 模拟 SHA-256 的 64 位十六进制 key。 */
	private static final String KEY_A = repeat('a', 64);

	private static final String KEY_B = repeat('b', 64);

	@TempDir
	Path root;

	@Test
	void putAll_thenGetAll_roundTripsExactFloats() {
		FileEmbeddingCacheStore store = new FileEmbeddingCacheStore(root);
		float[] vector = { 0.1f, -2.5f, Float.MIN_VALUE, 3.4028235e38f };

		store.putAll(Collections.singletonMap(KEY_A, vector));

		assertArrayEquals(vector, store.getAll(Collections.singletonList(KEY_A)).get(KEY_A));
	}

	@Test
	void getAll_missingKeys_areAbsentNotNull() {
		FileEmbeddingCacheStore store = new FileEmbeddingCacheStore(root);
		store.putAll(Collections.singletonMap(KEY_A, new float[] { 1f }));

		Map<String, float[]> hits = store.getAll(Arrays.asList(KEY_A, KEY_B));

		assertEquals(Collections.singleton(KEY_A), hits.keySet());
	}

	@Test
	void persistsAcrossInstances() {
		new FileEmbeddingCacheStore(root).putAll(Collections.singletonMap(KEY_A, new float[] { 7f, 8f }));

		float[] reloaded = new FileEmbeddingCacheStore(root).getAll(Collections.singletonList(KEY_A)).get(KEY_A);

		assertArrayEquals(new float[] { 7f, 8f }, reloaded);
	}

	@Test
	void layout_usesTwoCharSubdirectoryAndLeavesNoTempFiles() throws IOException {
		new FileEmbeddingCacheStore(root).putAll(Collections.singletonMap(KEY_A, new float[] { 1f }));

		assertEquals(Collections.singletonList("aa/" + KEY_A + ".bin"), listFiles(root));
	}

	@Test
	void forModel_sanitizesModelNameIntoDirectory() {
		FileEmbeddingCacheStore store = FileEmbeddingCacheStore.forModel(root, "qwen3-embedding:0.6b");

		// ":" 不能做 Windows 目录名，被替换成 "_"
		assertEquals(root.resolve("qwen3-embedding_0.6b"), store.getDirectory());
	}

	@Test
	void corruptFile_isTreatedAsMiss() throws IOException {
		FileEmbeddingCacheStore store = new FileEmbeddingCacheStore(root);
		store.putAll(Collections.singletonMap(KEY_A, new float[] { 1f, 2f, 3f }));
		// 只保留前 6 个字节：长度字段完整，但向量数据被截断
		Path file = root.resolve("aa").resolve(KEY_A + ".bin");
		Files.write(file, Arrays.copyOf(Files.readAllBytes(file), 6));

		Map<String, float[]> hits = store.getAll(Collections.singletonList(KEY_A));

		assertTrue(hits.isEmpty());
	}

	@Test
	void putAll_existingKey_isOverwritten() {
		FileEmbeddingCacheStore store = new FileEmbeddingCacheStore(root);
		store.putAll(Collections.singletonMap(KEY_A, new float[] { 1f }));

		store.putAll(Collections.singletonMap(KEY_A, new float[] { 9f, 9f }));

		assertArrayEquals(new float[] { 9f, 9f }, store.getAll(Collections.singletonList(KEY_A)).get(KEY_A));
	}

	/** 列出目录下所有文件的相对路径（用 / 分隔，方便跨平台断言）。 */
	private static List<String> listFiles(Path dir) throws IOException {
		List<String> result = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.filter(Files::isRegularFile)
				.forEach(p -> result.add(dir.relativize(p).toString().replace('\\', '/')));
		}
		return result;
	}

	private static String repeat(char c, int times) {
		char[] chars = new char[times];
		Arrays.fill(chars, c);
		return new String(chars);
	}

}
