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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 验证配置开关：只启动一个只包含 LegacySchemaLinker 的迷你容器，不加载整个应用。
 */
class SchemaLinkingModeTest {

	/** ApplicationContextRunner：每个用例启动一个独立的轻量容器，跑完自动关闭。 */
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withBean(SchemaService.class, () -> mock(SchemaService.class))
		.withUserConfiguration(LegacySchemaLinker.class);

	@Test
	void propertyMissing_registersLegacyByDefault() {
		runner.run(context -> assertThat(context).hasSingleBean(SchemaLinker.class)
			.getBean(SchemaLinker.class)
			.isInstanceOf(LegacySchemaLinker.class));
	}

	@Test
	void modeLegacy_registersLegacy() {
		runner.withPropertyValues(SchemaLinkingMode.PROPERTY + "=legacy")
			.run(context -> assertThat(context).hasSingleBean(LegacySchemaLinker.class));
	}

	@Test
	void otherMode_doesNotRegisterLegacy() {
		// 切到其他实现时，legacy 不能同时注册，否则节点注入 SchemaLinker 会有两个候选
		runner.withPropertyValues(SchemaLinkingMode.PROPERTY + "=multi-route")
			.run(context -> assertThat(context).doesNotHaveBean(SchemaLinker.class));
	}

	@Test
	void propertyName_isUnderProjectPrefix() {
		assertThat(SchemaLinkingMode.PROPERTY).isEqualTo("spring.ai.alibaba.data-agent.schema-linking.mode");
	}

}
