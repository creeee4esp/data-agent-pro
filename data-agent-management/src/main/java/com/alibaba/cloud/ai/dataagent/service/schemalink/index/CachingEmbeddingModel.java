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

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带缓存的 {@link EmbeddingModel}（装饰器模式）：同一模型、同一段文本只向底层模型请求一次。
 * <p>
 * 本类只负责<b>缓存策略</b>：算 key、查缓存、只把未命中的文本发给底层模型、按原顺序组装结果、统计命中。 向量存在哪里由
 * {@link EmbeddingCacheStore} 决定，换存储介质（文件 → Redis）只需换一个实现类，本类不用改。
 * <p>
 * 用途：召回评测要对成千上万的表、列文档反复向量化，缓存后第二次起几乎不再调用模型。
 * <p>
 * 限制：缓存命中时，响应中没有 token 用量等元数据；一个实例只对应一个模型名，请求选项中临时指定的其他模型不参与 key。
 */
public class CachingEmbeddingModel implements EmbeddingModel {

	private final EmbeddingModel delegate;

	private final String modelName;

	private final EmbeddingCacheStore store;

	private final AtomicLong hitCount = new AtomicLong();

	private final AtomicLong missCount = new AtomicLong();

	/**
	 * @param delegate 被装饰的真实模型
	 * @param modelName 模型名，例如 qwen3-embedding:0.6b；参与缓存 key，换模型不会读到旧向量
	 * @param store 缓存存储
	 */
	public CachingEmbeddingModel(EmbeddingModel delegate, String modelName, EmbeddingCacheStore store) {
		Assert.notNull(delegate, "delegate must not be null");
		Assert.hasText(modelName, "modelName must not be blank");
		Assert.notNull(store, "store must not be null");
		this.delegate = delegate;
		this.modelName = modelName;
		this.store = store;
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		List<String> texts = request.getInstructions();
		// 1. 每段文本算出 key，一次性批量查缓存（Set 去重：同一请求里的重复文本只查一次）
		List<String> keys = new ArrayList<>(texts.size());
		for (String text : texts) {
			keys.add(cacheKey(text));
		}
		Map<String, float[]> found = new LinkedHashMap<>(store.getAll(new LinkedHashSet<>(keys)));

		// 2.收集未命中的文本(按key去重，保持首次出现的顺序)
		Map<String, String> missTextByKey = new LinkedHashMap<>();
		for (int i = 0; i < keys.size(); i++) {
			if (!found.containsKey(keys.get(i))) {
				missTextByKey.putIfAbsent(keys.get(i), texts.get(i));

			}
		}

		// 3.只把未命中的文本发给底层模型，结果批量写回缓存
		if (!missTextByKey.isEmpty()) {
			List<String> missKeys = new ArrayList<>(missTextByKey.keySet());
			List<String> missTexts = new ArrayList<>(missTextByKey.values());
			EmbeddingResponse response = delegate.call(new EmbeddingRequest(missTexts, request.getOptions()));
			List<Embedding> results = response.getResults();
			Assert.state(results.size() == missTexts.size(),
					"delegate returned " + results.size() + " embeddings for " + missTexts.size() + " texts");
			Map<String, float[]> computed = new LinkedHashMap<>();
			for (int i = 0; i < missKeys.size(); i++) {
				computed.put(missKeys.get(i), results.get(i).getOutput());
			}
			store.putAll(computed);
			found.putAll(computed);
			missCount.addAndGet(missTexts.size());
		}
		hitCount.addAndGet(texts.size() - countMissPositions(keys, missTextByKey.keySet()));

		// 4 按原始顺序组装结果(重复文本的每个位置都有向量)
		List<Embedding> embeddings = new ArrayList<>(texts.size());
		for (int i = 0; i < texts.size(); i++) {
			embeddings.add(new Embedding(found.get(keys.get(i)), i));
		}
		return new EmbeddingResponse(embeddings);
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getText());
	}

	/** 维度由底层模型决定，直接转调（避免为了取维度去嵌入一段测试文本）。 */
	@Override
	public int dimensions() {
		return delegate.dimensions();
	}

	/** 从缓存取到的文本位置数。 */
	public long getHitCount() {
		return hitCount.get();
	}

	/** 实际请求底层模型的文本数（去重后）。评测报告用它统计"这次实验调用了多少次模型"。 */
	public long getMissCount() {
		return missCount.get();
	}

	/** 缓存 key = SHA-256(模型名 + 换行 + 文本)，64 个十六进制字符。 */
	String cacheKey(String text) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest((modelName + "\n" + text).getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			// 所有 JVM 都必须支持 SHA-256，不会发生
			throw new IllegalStateException(e);
		}
	}

	/** 统计有多少个位置属于"本次未命中"的 key（同一 key 出现多次时，每个位置都算）。 */
	private static int countMissPositions(List<String> keys, Set<String> missKeys) {
		int count = 0;
		for (String key : keys) {
			if (missKeys.contains(key)) {
				count++;
			}
		}
		return count;
	}

}
