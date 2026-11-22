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

import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import lombok.Getter;
import org.springframework.ai.document.Document;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 召回的输出
 */
@Getter
public final class LinkedSchema {

	private final List<Document> tableDocuments;

	private final List<Document> columnDocuments;

	private final LinkDiagnostics diagnostics;

	public LinkedSchema(List<Document> tableDocuments, List<Document> columnDocuments, LinkDiagnostics diagnostics) {
		Assert.notNull(diagnostics, "diagnostics must not be null");
		this.tableDocuments = immutableCopy(tableDocuments);
		this.columnDocuments = immutableCopy(columnDocuments);
		this.diagnostics = diagnostics;
	}

	public List<String> getTableNames() {
		return extractTableNames(tableDocuments);
	}

	public static List<String> extractTableNames(List<Document> tableDocuments) {
		List<String> names = new ArrayList<>();
		if (tableDocuments == null) {
			return names;
		}
		for (Document document : tableDocuments) {
			Object name = document.getMetadata().get(DocumentMetadataConstant.NAME);
			if (name instanceof String && StringUtils.hasText((String) name)) {
				names.add((String) name);
			}
		}
		return names;
	}

	private static List<Document> immutableCopy(List<Document> documents) {
		return CollectionUtils.isEmpty(documents) ? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(documents));
	}

}
