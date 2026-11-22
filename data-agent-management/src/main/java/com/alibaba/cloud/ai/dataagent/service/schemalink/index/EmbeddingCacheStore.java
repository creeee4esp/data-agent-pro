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
import java.util.Map;

/**
 * 向量缓存的存储层：只负责按 key 存取向量，不关心 key 怎么算、什么时候算命中。
 * <p>
 * 缓存策略（算 key、只请求未命中部分、组装结果）在 {@link CachingEmbeddingModel} 中；存储介质由实现类决定： 本地文件、内存、以后可以是
 * Redis 等。
 * <p>
 * 接口按<b>批量</b>设计：远程存储（如 Redis）可以用一次 MGET / pipeline 完成一批读写，避免逐条网络往返。
 * <p>
 * 实现类约定：
 * <ul>
 * <li>必须线程安全；</li>
 * <li>返回的向量数组由调用方持有，实现类不能在之后修改它；</li>
 * <li>单个条目读取失败（如文件损坏）应当作未命中，而不是让整批失败。</li>
 * </ul>
 */

public interface EmbeddingCacheStore {

	/**
	 * 批量读取。
	 * @param keys 要读取的 key
	 * @return 命中的 key → 向量；未命中的 key 不出现在结果中。不返回 null
	 */
	Map<String, float[]> getAll(Collection<String> keys);

	/**
	 * 批量写入，已存在的 key 会被覆盖。
	 * @param vectors key → 向量
	 */
	void putAll(Map<String, float[]> vectors);

}
