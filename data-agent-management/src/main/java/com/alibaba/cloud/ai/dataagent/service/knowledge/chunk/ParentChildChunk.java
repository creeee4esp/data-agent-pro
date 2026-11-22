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
package com.alibaba.cloud.ai.dataagent.service.knowledge.chunk;

import lombok.Value;

import java.util.List;

/**
 * 父子分块的一个子块。子块之间不重叠：同一父块的子块按 {@code childSeq} 排好、各取正文（{@link #getBody}）用换行拼接，就是父块全文。
 */
@Value
public class ParentChildChunk {

	// 父块在文档里的序号，从0开始
	int parentSeq;

	// 子块在父块里的序号，从0开始
	int childSeq;

	// 章节路径: 文档标题 > 章节标题
	String section;

	// 子块正文: 父块中连续的若干行,原样保留
	String body;

	// 子块从表格中间开始时，所在表格的表头行, 否则为 null
	String tableHeader;

	// 写入向量库的文本: 第一行是标题路径(表格续行时带表头)第二行起时正文
	public String toText() {
		String head = tableHeader == null ? section : section + " ｜ 表头 " + tableHeader;
		return "【" + head + "】\n" + body;
	}

	/**
	 * 由同一父块的子块文本（已按 childSeq 排好）拼回父块全文：去掉每个子块的第一行（标题路径），其余用换行拼接。
	 */
	public static String restoreParent(List<String> childTexts) {
		StringBuilder sb = new StringBuilder();
		for (String text : childTexts) {
			int nl = text.indexOf('\n');
			if (sb.length() > 0) {
				sb.append('\n');
			}
			sb.append(nl < 0 ? "" : text.substring(nl + 1));
		}
		return sb.toString();
	}

}
