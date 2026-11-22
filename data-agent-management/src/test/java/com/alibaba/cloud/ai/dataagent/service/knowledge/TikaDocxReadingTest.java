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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;

/**
 * 生产的文档读取路径（TikaDocumentReader）要把 docx 识别为 Word，而不是普通 zip。 POI 版本与 Tika 不匹配时，正文里会混进
 * [Content_Types].xml 等 zip 条目名。
 */
class TikaDocxReadingTest {

	@Test
	void docxIsParsedAsWordNotZip() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (XWPFDocument doc = new XWPFDocument()) {
			doc.createParagraph().createRun().setText("第一段正文");
			doc.write(out);
		}
		ByteArrayResource resource = new ByteArrayResource(out.toByteArray()) {
			@Override
			public String getFilename() {
				return "a.docx";
			}
		};

		String text = new TikaDocumentReader(resource).read().get(0).getText();

		assertThat(text).contains("第一段正文").doesNotContain("[Content_Types].xml");
	}

}
