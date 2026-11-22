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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.xml.sax.helpers.AttributesImpl;

class MarkdownConverterTest {

	private final MarkdownConverter converter = new MarkdownConverter();

	@Test
	void markdownAndTextAreReadAsIs() throws Exception {
		String md = "# 标题\r\n\r\n## 节\r\n内容\r\n";
		ByteArrayResource r = new ByteArrayResource(md.getBytes(StandardCharsets.UTF_8));

		assertThat(converter.convert(r, "a.md")).isEqualTo("# 标题\n\n## 节\n内容\n");
		assertThat(converter.convert(r, "a.TXT")).isEqualTo("# 标题\n\n## 节\n内容\n");
	}

	@Test
	void handlerWritesHeadingsListsAndTables() {
		MarkdownContentHandler h = new MarkdownContentHandler();
		element(h, "h1", "文档标题");
		element(h, "h2", "第一节");
		element(h, "p", "一段  正文\n换行");
		h.startElement("", "ul", "ul", new AttributesImpl());
		element(h, "li", "要点一");
		element(h, "li", "要点二");
		h.endElement("", "ul", "ul");
		h.startElement("", "table", "table", new AttributesImpl());
		row(h, "th", "列A", "列B");
		row(h, "td", "1", "2");
		h.endElement("", "table", "table");

		assertThat(h.markdown())
			.isEqualTo("# 文档标题\n\n## 第一节\n\n一段 正文 换行\n\n- 要点一\n- 要点二\n\n" + "| 列A | 列B |\n|---|---|\n| 1 | 2 |\n");
	}

	@Test
	void docxParagraphsAreConverted() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (XWPFDocument doc = new XWPFDocument()) {
			doc.createParagraph().createRun().setText("第一段内容");
			doc.createParagraph().createRun().setText("第二段内容");
			doc.write(out);
		}
		String md = converter.convert(new ByteArrayResource(out.toByteArray()), "a.docx");

		assertThat(md).isEqualTo("第一段内容\n\n第二段内容\n");
	}

	@Test
	void pdfTextIsConverted() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (PDDocument doc = new PDDocument()) {
			PDPage page = new PDPage();
			doc.addPage(page);
			try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
				cs.beginText();
				cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
				cs.newLineAtOffset(72, 700);
				cs.showText("Hello parent child");
				cs.endText();
			}
			doc.save(out);
		}
		String md = converter.convert(new ByteArrayResource(out.toByteArray()), "a.pdf");

		assertThat(md).contains("Hello parent child");
	}

	private static void element(MarkdownContentHandler h, String name, String text) {
		h.startElement("", name, name, new AttributesImpl());
		h.characters(text.toCharArray(), 0, text.length());
		h.endElement("", name, name);
	}

	private static void row(MarkdownContentHandler h, String cell, String... values) {
		h.startElement("", "tr", "tr", new AttributesImpl());
		for (String v : values) {
			element(h, cell, v);
		}
		h.endElement("", "tr", "tr");
	}

}
