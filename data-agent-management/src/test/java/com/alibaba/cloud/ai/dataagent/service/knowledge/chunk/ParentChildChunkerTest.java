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
package com.alibaba.cloud.ai.dataagent.service.knowledge.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.junit.jupiter.api.Test;

class ParentChildChunkerTest {

	private static ParentChildChunker chunker(int parentMax, int parentMin, int child) {
		DataAgentProperties p = new DataAgentProperties();
		p.getAgentKnowledge().getParentChild().setParentMaxChars(parentMax);
		p.getAgentKnowledge().getParentChild().setParentMinChars(parentMin);
		p.getAgentKnowledge().getParentChild().setChildChars(child);
		return new ParentChildChunker(p);
	}

	/** 按 parentSeq 分组拼回：parentSeq -> 父块全文。 */
	private static Map<Integer, String> restore(List<ParentChildChunk> chunks) {
		Map<Integer, List<ParentChildChunk>> byParent = chunks.stream()
			.collect(Collectors.groupingBy(ParentChildChunk::getParentSeq, TreeMap::new, Collectors.toList()));
		Map<Integer, String> out = new TreeMap<>();
		byParent.forEach((seq, list) -> {
			List<String> texts = new ArrayList<>();
			list.forEach(c -> texts.add(c.toText()));
			out.put(seq, ParentChildChunk.restoreParent(texts));
		});
		return out;
	}

	private static String nonBlank(String s) {
		return s.replaceAll("\\s+", "");
	}

	@Test
	void sectionsBecomeParentsWithHeadingPath() {
		String md = "# 表说明：t\n\n## 概述\n" + repeat("概述内容。", 30) + "\n\n## 字段\n" + repeat("- a：字段说明。\n", 20);
		List<ParentChildChunk> chunks = chunker(1500, 10, 200).split(md, "fallback");

		assertThat(chunks).extracting(ParentChildChunk::getSection).containsOnly("表说明：t > 概述", "表说明：t > 字段");
		assertThat(chunks.get(0).toText()).startsWith("【表说明：t > 概述】\n");
		Map<Integer, String> parents = restore(chunks);
		assertThat(parents).hasSize(2);
		assertThat(parents.get(1)).isEqualTo(repeat("- a：字段说明。\n", 20).trim());
	}

	@Test
	void splitThenRestoreKeepsAllText() {
		StringBuilder md = new StringBuilder("# 手册\n\n");
		for (int i = 0; i < 6; i++) {
			md.append("## 第").append(i).append("章\n\n");
			for (int j = 0; j < 8; j++) {
				md.append(repeat("第" + i + "章第" + j + "段的内容，用来测试切分。", 4)).append("\n\n");
			}
		}
		List<ParentChildChunk> chunks = chunker(600, 50, 120).split(md.toString(), "x");

		String restored = String.join("\n", restore(chunks).values());
		String expected = md.toString().replaceAll("(?m)^#+ .*$", "");
		assertThat(nonBlank(restored)).isEqualTo(nonBlank(expected));
		assertThat(chunks).allMatch(c -> c.getBody().replace("\n", "").length() <= 240);
	}

	@Test
	void shortSectionIsMergedIntoPreviousKeepingItsHeading() {
		String md = "# 文档\n\n## 长节\n" + repeat("很长的内容。", 40) + "\n\n## 短节\n短。\n";
		List<ParentChildChunk> chunks = chunker(1500, 100, 500).split(md, "x");

		Map<Integer, String> parents = restore(chunks);
		assertThat(parents).hasSize(1);
		assertThat(parents.get(0)).contains("## 短节").contains("短。");
	}

	@Test
	void documentWithoutHeadingsIsSplitByParagraphs() {
		String para = repeat("没有标题的段落内容。", 20);
		String md = para + "\n\n" + para + "\n\n" + para + "\n";
		List<ParentChildChunk> chunks = chunker(300, 10, 200).split(md, "无标题文档");

		assertThat(chunks).extracting(ParentChildChunk::getSection).containsOnly("无标题文档");
		assertThat(restore(chunks)).hasSize(3);
	}

	@Test
	void tableContinuationCarriesHeader() {
		StringBuilder md = new StringBuilder("# 报告\n\n## 分维度\n| 省份 | 金额 |\n|---|---|\n");
		for (int i = 0; i < 30; i++) {
			md.append("| 省份").append(i).append(" | ").append(1000 + i).append(" 元 |\n");
		}
		List<ParentChildChunk> chunks = chunker(5000, 10, 100).split(md.toString(), "x");

		assertThat(chunks.size()).isGreaterThan(2);
		assertThat(chunks.get(0).getTableHeader()).isNull();
		assertThat(chunks.get(1).getTableHeader()).isEqualTo("| 省份 | 金额 |");
		assertThat(chunks.get(1).toText()).startsWith("【报告 > 分维度 ｜ 表头 | 省份 | 金额 |】\n");
	}

	@Test
	void headingsInsideCodeFenceAreIgnored() {
		String md = "# 文档\n\n## 加工逻辑\n" + repeat("说明。", 40) + "\n```sql\n# not a heading\nSELECT 1\n```\n";
		List<ParentChildChunk> chunks = chunker(1500, 10, 500).split(md, "x");

		assertThat(chunks).extracting(ParentChildChunk::getSection).containsOnly("文档 > 加工逻辑");
		assertThat(restore(chunks).get(0)).contains("# not a heading");
	}

	private static String repeat(String s, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			sb.append(s);
		}
		return sb.toString();
	}

}
