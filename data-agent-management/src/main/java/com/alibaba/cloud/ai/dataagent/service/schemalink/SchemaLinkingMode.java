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
package com.alibaba.cloud.ai.dataagent.service.schemalink;

import com.alibaba.cloud.ai.dataagent.constant.Constant;

/**
 * Schema 召回实现的切换开关。
 * <p>
 * 配置项 {@value #PROPERTY}，不配置时默认 {@value #LEGACY}。每个 {@link SchemaLinker} 实现用
 * {@code @ConditionalOnProperty(name = PROPERTY, havingValue = 自己的名字)} 声明自己对应哪个取值，
 * 容器里同一时间只会注册一个实现，节点按类型注入不会有歧义。 配置了不存在的取值时，应用启动失败（找不到 SchemaLinker），而不是悄悄回退到某个实现。
 */
public final class SchemaLinkingMode {

	/** 配置项全名：spring.ai.alibaba.data-agent.schema-linking.mode。 */
	public static final String PROPERTY = Constant.PROJECT_PROPERTIES_PREFIX + ".schema-linking.mode";

	/** 原有召回逻辑，默认值。 */
	public static final String LEGACY = LegacySchemaLinker.NAME;

	private SchemaLinkingMode() {
	}

}
