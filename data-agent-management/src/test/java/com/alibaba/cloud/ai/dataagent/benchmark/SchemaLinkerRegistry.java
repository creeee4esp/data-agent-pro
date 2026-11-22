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
package com.alibaba.cloud.ai.dataagent.benchmark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.alibaba.cloud.ai.dataagent.service.schemalink.AgenticSchemaLinker;
import com.alibaba.cloud.ai.dataagent.service.schemalink.LegacySchemaLinker;
import com.alibaba.cloud.ai.dataagent.service.schemalink.SchemaCompletionAgent;
import com.alibaba.cloud.ai.dataagent.service.schemalink.SchemaLinker;

/**
 * 评测器可选的 SchemaLinker 实现：名字 → 用评测环境创建实例的函数。
 * <p>
 * 名字与生产配置 {@code spring.ai.alibaba.data-agent.schema-linking.mode} 的取值一致。S3
 * 新增实现时，在这里注册一行即可， 评测器其余代码不用改：{@code -Dbenchmark.linker=新名字}。
 */
public final class SchemaLinkerRegistry {

	private static final Map<String, Function<BenchmarkEnvironment, SchemaLinker>> FACTORIES;

	static {
		// LinkedHashMap：报错时按注册顺序列出可选值
		Map<String, Function<BenchmarkEnvironment, SchemaLinker>> map = new LinkedHashMap<>();
		map.put(LegacySchemaLinker.NAME, env -> new LegacySchemaLinker(env.getSchemaService()));
		map.put(AgenticSchemaLinker.NAME,
				env -> new AgenticSchemaLinker(env.getSchemaService(), env.getVectorStoreService(),
						new SchemaCompletionAgent(env.getAiModelRegistry(), env.getSchemaService(),
								env.getVectorStoreService(), env.getDatabaseUtil(), env.getProperties())));
		// 只做热启动，不调用大模型（消融）
		map.put(AgenticSchemaLinker.NAME + "-warm",
				env -> new AgenticSchemaLinker(env.getSchemaService(), env.getVectorStoreService(), null));
		FACTORIES = Collections.unmodifiableMap(map);
	}

	private SchemaLinkerRegistry() {
	}

	/**
	 * 按名字创建实现。
	 * @throws IllegalArgumentException 名字未注册，消息中列出可选值
	 */
	public static SchemaLinker create(String name, BenchmarkEnvironment env) {
		Function<BenchmarkEnvironment, SchemaLinker> factory = FACTORIES.get(name);
		if (factory == null) {
			throw new IllegalArgumentException("Unknown linker '" + name + "', available: " + FACTORIES.keySet());
		}
		return factory.apply(env);
	}

	/** 已注册的实现名。 */
	public static Set<String> names() {
		return FACTORIES.keySet();
	}

}
