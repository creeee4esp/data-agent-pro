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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 把 Tika 解析出的 XHTML 事件直接写成 Markdown：标题、段落、列表、表格保留结构，其余元素只取文本。
 * <p>
 * docx 用了 Word 标题样式时会得到 h1～h6；PDF 没有标题信息，只有段落。
 */
class MarkdownContentHandler extends DefaultHandler {

	private final StringBuilder out = new StringBuilder();

	/** 当前块（标题、段落、列表项、单元格）的文本。 */
	private final StringBuilder block = new StringBuilder();

	/** 嵌套的列表类型：ul / ol，以及 ol 的当前序号。 */
	private final Deque<int[]> lists = new ArrayDeque<>();

	private List<List<String>> table;

	private List<String> row;

	private String blockPrefix;

	@Override
	public void startElement(String uri, String localName, String qName, Attributes atts) {
		String name = localName.isEmpty() ? qName : localName;
		switch (name) {
			case "h1":
			case "h2":
			case "h3":
			case "h4":
			case "h5":
			case "h6":
				startBlock(repeat('#', name.charAt(1) - '0') + " ");
				break;
			case "p":
				if (table == null) {
					startBlock("");
				}
				break;
			case "ul":
				lists.push(new int[] { 0, 0 });
				break;
			case "ol":
				lists.push(new int[] { 1, 0 });
				break;
			case "li":
				int[] list = lists.isEmpty() ? new int[] { 0, 0 } : lists.peek();
				list[1]++;
				String indent = repeat(' ', 2 * Math.max(0, lists.size() - 1));
				startBlock(indent + (list[0] == 1 ? list[1] + ". " : "- "));
				break;
			case "table":
				table = new ArrayList<>();
				break;
			case "tr":
				row = new ArrayList<>();
				break;
			case "th":
			case "td":
				block.setLength(0);
				break;
			case "br":
				block.append(' ');
				break;
			default:
				break;
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) {
		String name = localName.isEmpty() ? qName : localName;
		switch (name) {
			case "h1":
			case "h2":
			case "h3":
			case "h4":
			case "h5":
			case "h6":
			case "li":
				endBlock();
				break;
			case "p":
				if (table == null) {
					endBlock();
				}
				break;
			case "ul":
			case "ol":
				if (!lists.isEmpty()) {
					lists.pop();
				}
				if (lists.isEmpty()) {
					out.append('\n');
				}
				break;
			case "th":
			case "td":
				if (row != null) {
					row.add(clean(block.toString()).replace("|", "\\|"));
				}
				block.setLength(0);
				break;
			case "tr":
				if (table != null && row != null && !row.isEmpty()) {
					table.add(row);
				}
				row = null;
				break;
			case "table":
				writeTable();
				table = null;
				break;
			default:
				break;
		}
	}

	@Override
	public void characters(char[] ch, int start, int length) {
		block.append(ch, start, length);
	}

	/** 转换结果：去掉多余的空行，末尾保留一个换行。 */
	String markdown() {
		return out.toString().replaceAll("\n{3,}", "\n\n").trim() + "\n";
	}

	private void startBlock(String prefix) {
		blockPrefix = prefix;
		block.setLength(0);
	}

	private void endBlock() {
		String text = clean(block.toString());
		if (!text.isEmpty() && blockPrefix != null) {
			boolean listItem = !blockPrefix.isEmpty() && !blockPrefix.startsWith("#");
			out.append(blockPrefix).append(text).append(listItem ? "\n" : "\n\n");
		}
		blockPrefix = null;
		block.setLength(0);
	}

	private void writeTable() {
		if (table == null || table.isEmpty()) {
			return;
		}
		int cols = 0;
		for (List<String> r : table) {
			cols = Math.max(cols, r.size());
		}
		for (int i = 0; i < table.size(); i++) {
			List<String> r = table.get(i);
			StringBuilder line = new StringBuilder("|");
			for (int c = 0; c < cols; c++) {
				line.append(' ').append(c < r.size() ? r.get(c) : "").append(" |");
			}
			out.append(line).append('\n');
			if (i == 0) {
				out.append('|').append(repeat("---|", cols)).append('\n');
			}
		}
		out.append('\n');
	}

	/** 合并连续空白；Tika 会把段内换行、制表符原样传过来。 */
	private static String clean(String s) {
		return s.replaceAll("\\s+", " ").trim();
	}

	private static String repeat(char c, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			sb.append(c);
		}
		return sb.toString();
	}

	private static String repeat(String s, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++) {
			sb.append(s);
		}
		return sb.toString();
	}

}
