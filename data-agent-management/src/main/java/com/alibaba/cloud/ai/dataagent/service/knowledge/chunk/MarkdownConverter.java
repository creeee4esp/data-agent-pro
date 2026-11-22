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

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 把上传的文件统一转成 Markdown，供父子分块按标题切分。
 * <ul>
 * <li>md / markdown / txt：按 UTF-8 原样读取（txt 里有 # 标题时同样能识别）；</li>
 * <li>其他格式（docx、doc、pdf 等）：Tika 解析成 XHTML，再由 {@link MarkdownContentHandler} 写成
 * Markdown。</li>
 * </ul>
 */
@Component
public class MarkdownConverter {

	public String convert(Resource resource, String filename) throws IOException {
		String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
		try (InputStream in = resource.getInputStream()) {
			if (name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt")) {
				return StreamUtils.copyToString(in, StandardCharsets.UTF_8).replace("\r\n", "\n");
			}
			MarkdownContentHandler handler = new MarkdownContentHandler();
			Metadata metadata = new Metadata();
			metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
			// TikaInputStream 支持多次 mark/reset，docx 等 zip 容器格式才能被识别出具体类型
			try (TikaInputStream tis = TikaInputStream.get(in)) {
				new AutoDetectParser().parse(tis, handler, metadata, new ParseContext());
			}
			return handler.markdown();
		}
		catch (SAXException | TikaException e) {
			throw new IOException("Cannot convert " + filename + " to markdown: " + e.getMessage(), e);
		}
	}

}
