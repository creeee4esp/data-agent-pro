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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import org.springframework.stereotype.Component;

/**
 * 父子分块（ADR-012）：Markdown 按标题切出父块（章节），父块内按行切出不重叠的子块。
 * <ol>
 * <li>第一个一级标题是文档标题；二～六级标题形成章节路径"文档标题 > 章节 > 小节"，最深一级的每一节是一个父块；</li>
 * <li>短于 {@code parentMinChars} 的章节并入前一个父块（保留它的标题行）；长于 {@code parentMaxChars}
 * 的按段落拆成多个父块；</li>
 * <li>超过 {@code childChars} 的长行先在句末断开；子块由父块中连续的行组成，长度接近 {@code childChars}，尽量在空行处断开；</li>
 * <li>没有标题的文档（PDF、纯文本）整篇是一个章节，按段落拆父块。</li>
 * </ol>
 */
@Component
public class ParentChildChunker {

	private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

	/** 句末：中文句号、问号、叹号、分号，以及后面跟空白的英文句末标点。 */
	private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？；])|(?<=[.!?;])(?=\\s)");

	private final int parentMaxChars;

	private final int parentMinChars;

	private final int childChars;

	public ParentChildChunker(DataAgentProperties properties) {
		DataAgentProperties.ParentChild config = properties.getAgentKnowledge().getParentChild();
		this.parentMaxChars = config.getParentMaxChars();
		this.parentMinChars = config.getParentMinChars();
		this.childChars = config.getChildChars();
	}

	/**
	 * @param markdown 文档全文
	 * @param fallbackTitle 文档里没有一级标题时用的标题（通常是知识标题或文件名）
	 */
	public List<ParentChildChunk> split(String markdown, String fallbackTitle) {
		List<ParentChildChunk> chunks = new ArrayList<>();
		int parentSeq = 0;
		for (Section parent : parents(markdown, fallbackTitle)) {
			int childSeq = 0;
			for (String[] child : children(parent.lines)) {
				chunks.add(new ParentChildChunk(parentSeq, childSeq++, parent.path, child[0], child[1]));
			}
			parentSeq++;
		}
		return chunks;
	}

	// ---------- 父块 ----------

	private List<Section> parents(String markdown, String fallbackTitle) {
		List<String> lines = new ArrayList<>(Arrays.asList(markdown.replace("\r\n", "\n").split("\n", -1)));
		String title = fallbackTitle;
		String[] heads = new String[7];
		List<Section> sections = new ArrayList<>();
		Section current = null;
		boolean fence = false;
		boolean titleSeen = false;
		for (String line : lines) {
			if (line.trim().startsWith("```")) {
				fence = !fence;
			}
			Matcher m = fence ? null : HEADING.matcher(line);
			if (m != null && m.matches()) {
				int level = m.group(1).length();
				if (level == 1 && !titleSeen) {
					title = m.group(2).trim();
					titleSeen = true;
					continue;
				}
				heads[level] = m.group(2).trim();
				for (int i = level + 1; i < heads.length; i++) {
					heads[i] = null;
				}
				current = new Section(path(title, heads), line);
				sections.add(current);
				continue;
			}
			if (current == null) {
				current = new Section(title, null);
				sections.add(current);
			}
			current.lines.add(line);
		}
		// 只有标题、没有正文的章节（例如 "## 一" 后面直接是 "### 1.1"）不成为父块
		List<Section> result = new ArrayList<>();
		for (Section s : sections) {
			s.trim();
			if (s.lines.isEmpty()) {
				continue;
			}
			Section last = result.isEmpty() ? null : result.get(result.size() - 1);
			if (last != null && s.length() < parentMinChars) {
				// 短章节并入前一个父块，保留它的标题行，正文不丢
				last.lines.add("");
				if (s.headingLine != null) {
					last.lines.add(s.headingLine);
				}
				last.lines.addAll(s.lines);
			}
			else {
				result.add(s);
			}
		}
		List<Section> split = new ArrayList<>();
		for (Section s : result) {
			s.lines = breakLongLines(s.lines);
			split.addAll(splitLong(s));
		}
		return split;
	}

	private static String path(String title, String[] heads) {
		StringBuilder sb = new StringBuilder(title);
		for (int i = 2; i < heads.length; i++) {
			if (heads[i] != null) {
				sb.append(" > ").append(heads[i]);
			}
		}
		return sb.toString();
	}

	/** 长章节按段落（空行）拆成多个父块，每块不超过 parentMaxChars（单个段落超长时单独成块）。 */
	private List<Section> splitLong(Section s) {
		List<Section> out = new ArrayList<>();
		if (s.length() <= parentMaxChars) {
			out.add(s);
			return out;
		}
		Section cur = new Section(s.path, null);
		List<String> para = new ArrayList<>();
		for (int i = 0; i <= s.lines.size(); i++) {
			boolean end = i == s.lines.size();
			String line = end ? "" : s.lines.get(i);
			if (!end && !line.trim().isEmpty()) {
				para.add(line);
				continue;
			}
			if (para.isEmpty()) {
				continue;
			}
			int paraLen = length(para);
			if (!cur.lines.isEmpty() && cur.length() + paraLen > parentMaxChars) {
				out.add(cur);
				cur = new Section(s.path, null);
			}
			if (!cur.lines.isEmpty()) {
				cur.lines.add("");
			}
			cur.lines.addAll(para);
			para = new ArrayList<>();
		}
		if (!cur.lines.isEmpty()) {
			out.add(cur);
		}
		return out;
	}

	/** 超过 childChars 的行在句末断开成多行；没有句末标点时按长度硬切。 */
	private List<String> breakLongLines(List<String> lines) {
		List<String> out = new ArrayList<>();
		for (String line : lines) {
			if (line.length() <= childChars || line.trim().startsWith("|")) {
				out.add(line);
				continue;
			}
			StringBuilder cur = new StringBuilder();
			for (String sentence : SENTENCE_END.split(line)) {
				if (cur.length() > 0 && cur.length() + sentence.length() > childChars) {
					out.addAll(hardCut(cur.toString()));
					cur.setLength(0);
				}
				cur.append(sentence);
			}
			if (cur.length() > 0) {
				out.addAll(hardCut(cur.toString()));
			}
		}
		return out;
	}

	private List<String> hardCut(String s) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i < s.length(); i += childChars) {
			out.add(s.substring(i, Math.min(s.length(), i + childChars)));
		}
		return out;
	}

	// ---------- 子块 ----------

	/** 返回 [正文, 表头或 null]。行按原顺序分组，组与组之间不重叠。 */
	private List<String[]> children(List<String> lines) {
		List<String[]> out = new ArrayList<>();
		List<String> cur = new ArrayList<>();
		int curLen = 0;
		String tableHeader = null;
		String curHeader = null;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			boolean tableRow = line.trim().startsWith("|");
			if (tableRow && (i == 0 || !lines.get(i - 1).trim().startsWith("|"))) {
				tableHeader = line.trim();
			}
			else if (!tableRow) {
				tableHeader = null;
			}
			boolean hasContent = curLen > 0;
			// 超长时断开；优先在空行处断开：当前已过半且遇到空行时也断开
			boolean full = hasContent && curLen + line.length() > childChars;
			boolean softBreak = hasContent && line.trim().isEmpty() && curLen >= childChars / 2;
			if (full || softBreak) {
				out.add(new String[] { String.join("\n", cur), curHeader });
				cur = new ArrayList<>();
				curLen = 0;
				// 新子块从表格中间（非表头行）开始时，带上表头
				curHeader = tableRow && !line.trim().equals(tableHeader) ? tableHeader : null;
			}
			cur.add(line);
			curLen += line.trim().length();
		}
		if (!cur.isEmpty()) {
			if (curLen == 0 && !out.isEmpty()) {
				// 只剩空行：并入上一个子块，保证拼回时不丢行
				String[] last = out.get(out.size() - 1);
				last[0] = last[0] + "\n" + String.join("\n", cur);
			}
			else {
				out.add(new String[] { String.join("\n", cur), curHeader });
			}
		}
		return out;
	}

	private static int length(List<String> lines) {
		int n = 0;
		for (String l : lines) {
			n += l.trim().length();
		}
		return n;
	}

	/** 一个章节：路径、标题行（用于并入前一节时保留）、正文行。 */
	private static final class Section {

		private final String path;

		private final String headingLine;

		private List<String> lines = new ArrayList<>();

		Section(String path, String headingLine) {
			this.path = path;
			this.headingLine = headingLine;
		}

		int length() {
			return ParentChildChunker.length(lines);
		}

		/** 去掉首尾空行。 */
		void trim() {
			while (!lines.isEmpty() && lines.get(0).trim().isEmpty()) {
				lines.remove(0);
			}
			while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
				lines.remove(lines.size() - 1);
			}
		}

	}

}
