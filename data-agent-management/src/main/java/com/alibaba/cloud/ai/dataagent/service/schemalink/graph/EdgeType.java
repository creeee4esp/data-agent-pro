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

/**
 * 表关系图里边的来源。枚举顺序同时用作置信度相同时的优先级。
 */
public enum EdgeType {

	/** 数据库里的物理外键，最可信。 */
	FOREIGN_KEY,

	/** 人工在数据源上配置的逻辑关系。 */
	LOGICAL_RELATION,

	/** 按列名推断出来的关系，可信度最低。 */
	NAMING_INFERENCE

}
