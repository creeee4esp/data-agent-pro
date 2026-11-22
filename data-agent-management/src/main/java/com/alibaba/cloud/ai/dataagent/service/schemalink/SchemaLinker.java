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
package com.alibaba.cloud.ai.dataagent.service.schemalink;

/**
 * Schema 召回（schema linking）：根据自然语言问题找出相关的表和列。
 */
public interface SchemaLinker {

	/**
	 * 召回与问题相关的表文档和列文档。
	 * <ul>
	 * <li>永不返回 {@code null}；召回不到时，返回结果中的列表为空。</li>
	 * <li>底层向量库或数据库抛出的异常原样抛出，不做包装，调用方保持原有的错误处理。</li>
	 * </ul>
	 * @param request 查询与数据源
	 * @return 表文档、列文档和诊断信息，不为 {@code null}
	 */
	LinkedSchema link(SchemaLinkRequest request);

}
