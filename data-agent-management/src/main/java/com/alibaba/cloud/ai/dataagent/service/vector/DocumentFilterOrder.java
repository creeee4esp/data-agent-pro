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
package com.alibaba.cloud.ai.dataagent.service.vector;

import org.springframework.ai.document.Document;

import java.util.Comparator;

/**
 * 按 metadata 精确查询（{@code findByFilter}）时文档的固定排序规则。
 * <p>
 * 内存向量库的底层 Map 以随机 UUID 为 key，ES 的过滤查询在分数相同时顺序取决于索引内部结构。两者直接返回都会让拼出来的 schema / prompt
 * 每次不同，因此统一按"表名 → 名称 → 文本 → id"排序。
 */
public final class DocumentFilterOrder {

	/**
	 * 先按表名把同一张表的文档放在一起，再按名称、文本；id 作为最后的兜底，保证完全相同的文档也有确定顺序。 字段缺失时按字符串 {@code "null"}
	 * 参与排序，与内存向量库原有实现一致。
	 */
	public static final Comparator<Document> ORDER = Comparator
		.comparing((Document document) -> String.valueOf(document.getMetadata().get("tableName")))
		.thenComparing(document -> String.valueOf(document.getMetadata().get("name")))
		.thenComparing(document -> String.valueOf(document.getText()))
		.thenComparing(document -> String.valueOf(document.getId()));

	private DocumentFilterOrder() {
	}

}
