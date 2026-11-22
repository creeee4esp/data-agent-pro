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
package com.alibaba.cloud.ai.dataagent.benchmark;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.entity.AgentKnowledge;
import com.alibaba.cloud.ai.dataagent.entity.BusinessKnowledge;
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.alibaba.cloud.ai.dataagent.mapper.AgentKnowledgeMapper;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.knowledge.AgentKnowledgeResourceManager;
import com.alibaba.cloud.ai.dataagent.service.knowledge.TextSplitterFactory;
import com.alibaba.cloud.ai.dataagent.service.knowledge.chunk.MarkdownConverter;
import com.alibaba.cloud.ai.dataagent.service.knowledge.chunk.ParentChildChunker;
import com.alibaba.cloud.ai.dataagent.util.DocumentConverterUtil;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

/**
 * 把 tools/dataset-gen/knowledge 的语料写进评测向量库，供端到端评测的证据召回使用：
 * <ul>
 * <li>业务知识 business.jsonl：与生产 {@code BusinessKnowledgeServiceImpl} 相同，用
 * {@link DocumentConverterUtil#convertBusinessKnowledgeToDocument} 转成文档；</li>
 * <li>智能体知识（文档 manifest.jsonl、QA / FAQ qa_faq.jsonl）：走生产的
 * {@link AgentKnowledgeResourceManager}，文档按父子分块切块。</li>
 * </ul>
 * 知识 id 与 {@code KnowledgeRetrievalBenchmarkIT} 相同（文档 1..N，QA / FAQ 从 100001
 * 起）。生产的证据召回节点按 id 查数据库取标题和答案，这里用 {@link #mapper()} 返回一个按内存数据应答的 mock。
 */
@Slf4j
final class KnowledgeCorpusLoader {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final Map<Integer, AgentKnowledge> knowledgeById = new HashMap<>();

	private KnowledgeCorpusLoader() {
	}

	/** 写入全部语料，返回加载器（持有知识 id → 实体，用于 mock 数据库）。 */
	static KnowledgeCorpusLoader load(BenchmarkEnvironment env, Path dir, String agentId) throws Exception {
		KnowledgeCorpusLoader loader = new KnowledgeCorpusLoader();
		long start = System.nanoTime();
		List<JsonNode> business = readJsonl(dir.resolve("business.jsonl"));
		List<Document> businessDocs = new ArrayList<>();
		for (int i = 0; i < business.size(); i++) {
			JsonNode b = business.get(i);
			BusinessKnowledge k = new BusinessKnowledge();
			k.setId((long) (i + 1));
			k.setAgentId(Long.valueOf(agentId));
			k.setBusinessTerm(b.get("businessTerm").asText());
			k.setDescription(b.get("description").asText());
			List<String> synonyms = new ArrayList<>();
			b.path("synonyms").forEach(s -> synonyms.add(s.asText()));
			k.setSynonyms(String.join(",", synonyms));
			businessDocs.add(DocumentConverterUtil.convertBusinessKnowledgeToDocument(k));
		}
		env.getVectorStoreService().addDocuments(agentId, businessDocs);

		AgentKnowledgeResourceManager manager = new AgentKnowledgeResourceManager(
				new TextSplitterFactory(
						Collections.singletonMap("recursive", new RecursiveCharacterTextSplitter(1000))),
				localFiles(), env.getVectorStoreService(), new MarkdownConverter(),
				new ParentChildChunker(env.getProperties()));
		List<JsonNode> manifest = readJsonl(dir.resolve("manifest.jsonl"));
		for (int i = 0; i < manifest.size(); i++) {
			JsonNode m = manifest.get(i);
			AgentKnowledge k = knowledge(i + 1, agentId, KnowledgeType.DOCUMENT, m.get("title").asText());
			Path file = dir.resolve(m.get("file").asText()).toAbsolutePath();
			k.setFilePath(file.toString());
			k.setSourceFilename(file.getFileName().toString());
			k.setSplitterType("parent-child");
			manager.doEmbedingToVectorStore(k);
			loader.knowledgeById.put(k.getId(), k);
		}
		List<JsonNode> qaFaq = readJsonl(dir.resolve("qa_faq.jsonl"));
		for (int i = 0; i < qaFaq.size(); i++) {
			JsonNode q = qaFaq.get(i);
			KnowledgeType type = "QA".equals(q.get("type").asText()) ? KnowledgeType.QA : KnowledgeType.FAQ;
			AgentKnowledge k = knowledge(100001 + i, agentId, type, q.get("title").asText());
			k.setQuestion(q.get("question").asText());
			k.setContent(q.get("answer").asText());
			manager.doEmbedingToVectorStore(k);
			loader.knowledgeById.put(k.getId(), k);
		}
		env.refresh();
		log.info("Knowledge corpus loaded: {} business terms, {} documents, {} QA/FAQ in {} ms", business.size(),
				manifest.size(), qaFaq.size(), (System.nanoTime() - start) / 1_000_000);
		return loader;
	}

	/** 按 id 返回内存里的知识实体（证据召回节点用它取标题、来源文件和 QA 答案）。 */
	AgentKnowledgeMapper mapper() {
		AgentKnowledgeMapper mapper = mock(AgentKnowledgeMapper.class);
		when(mapper.selectById(anyInt())).thenAnswer(invocation -> knowledgeById.get(invocation.getArgument(0)));
		return mapper;
	}

	private static AgentKnowledge knowledge(int id, String agentId, KnowledgeType type, String title) {
		AgentKnowledge k = new AgentKnowledge();
		k.setId(id);
		k.setAgentId(Integer.valueOf(agentId));
		k.setType(type);
		k.setTitle(title);
		k.setIsRecall(1);
		return k;
	}

	private static List<JsonNode> readJsonl(Path file) throws IOException {
		List<JsonNode> rows = new ArrayList<>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			if (!line.trim().isEmpty()) {
				rows.add(MAPPER.readTree(line));
			}
		}
		return rows;
	}

	/** 文件就在本地：FileStorageService 只需按路径返回资源。 */
	private static FileStorageService localFiles() {
		return new FileStorageService() {
			@Override
			public Mono<String> storeFile(FilePart filePart, String subPath) {
				throw new UnsupportedOperationException();
			}

			@Override
			public String storeFile(MultipartFile file, String subPath) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean deleteFile(String filePath) {
				return false;
			}

			@Override
			public String getFileUrl(String filePath) {
				return filePath;
			}

			@Override
			public Resource getFileResource(String filePath) {
				return new FileSystemResource(filePath);
			}
		};
	}

}
