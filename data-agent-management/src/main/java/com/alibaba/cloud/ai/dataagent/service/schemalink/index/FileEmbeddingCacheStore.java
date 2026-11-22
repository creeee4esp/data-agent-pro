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

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地文件实现：每个 key 一个二进制文件，路径为 {@code 目录/key前两位/key.bin}。
 * <ul>
 * <li>文件格式：[int 长度][float × 长度]，1024 维约 4KB；</li>
 * <li>按 key 前两位分子目录（256 个），避免单个目录下文件过多；</li>
 * <li>写入时先写临时文件再原子改名，进程中途被杀不会留下半个文件；并发写同一个 key 也安全。</li>
 * </ul>
 */
@Slf4j
public class FileEmbeddingCacheStore implements EmbeddingCacheStore {

	private final Path directory;

	/**
	 * @param directory 缓存目录，不存在时在第一次写入时自动创建
	 */
	public FileEmbeddingCacheStore(Path directory) {
		Assert.notNull(directory, "directory must not be null");
		this.directory = directory;
	}

	/**
	 * 按模型名在根目录下建子目录，便于人工查看和按模型清理缓存。 模型名里的 ":" "/" 等不能做目录名的字符替换成下划线，例如
	 * {@code qwen3-embedding:0.6b} → {@code qwen3-embedding_0.6b}。
	 */
	public static FileEmbeddingCacheStore forModel(Path cacheRoot, String modelName) {
		Assert.notNull(cacheRoot, "cacheRoot must not be null");
		Assert.hasText(modelName, "modelName must not be blank");
		return new FileEmbeddingCacheStore(cacheRoot.resolve(modelName.replaceAll("[^A-Za-z0-9._-]", "_")));
	}

	public Path getDirectory() {
		return directory;
	}

	@Override
	public Map<String, float[]> getAll(Collection<String> keys) {
		Map<String, float[]> hits = new LinkedHashMap<>();
		for (String key : keys) {
			float[] vector = read(key);
			if (vector != null) {
				hits.put(key, vector);
			}
		}
		return hits;
	}

	@Override
	public void putAll(Map<String, float[]> vectors) {
		for (Map.Entry<String, float[]> entry : vectors.entrySet()) {
			write(entry.getKey(), entry.getValue());
		}
	}

	private Path pathOf(String key) {
		Assert.isTrue(key.length() > 2, "key too short: " + key);
		return directory.resolve(key.substring(0, 2)).resolve(key + ".bin");
	}

	/** 读一个条目；不存在返回 null；文件损坏时记录警告并当作未命中。 */
	private float[] read(String key) {
		Path file = pathOf(key);
		if (!Files.exists(file)) {
			return null;
		}
		try (InputStream in = Files.newInputStream(file); DataInputStream data = new DataInputStream(in)) {
			int length = data.readInt();
			float[] vector = new float[length];
			for (int i = 0; i < length; i++) {
				vector[i] = data.readFloat();
			}
			return vector;
		}
		catch (IOException e) {
			log.warn("Ignoring unreadable embedding cache file {}: {}", file, e.getMessage());
			return null;
		}
	}

	/** 写一个条目：先写同目录下的临时文件，再原子改名为目标文件。 */
	private void write(String key, float[] vector) {
		Path file = pathOf(key);
		try {
			Files.createDirectories(file.getParent());
			Path tmp = Files.createTempFile(file.getParent(), key, ".tmp");
			try (OutputStream out = Files.newOutputStream(tmp); DataOutputStream data = new DataOutputStream(out)) {
				data.writeInt(vector.length);
				for (float v : vector) {
					data.writeFloat(v);
				}
			}
			try {
				Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException e) {
				// 极少数文件系统不支持原子改名，退化为普通替换
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to write embedding cache " + file, e);
		}
	}

}
