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
package com.alibaba.cloud.ai.dataagent.service.schemalink.graph;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 表关系图上的一个节点
 */
@Getter
@ToString
@EqualsAndHashCode(of = "name")
public final class TableNode {

	private final String name;

	/**
	 * 是否为表家族的代表表。只有代表表能当路径端点和命名推断的端点，派生表只做中间节点
	 */
	private final boolean representative;

	public TableNode(String name, boolean representative) {
		this.name = name;
		this.representative = representative;
	}

}
