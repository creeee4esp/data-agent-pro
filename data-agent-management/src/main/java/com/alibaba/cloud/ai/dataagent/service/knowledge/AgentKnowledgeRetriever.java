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
package com.alibaba.cloud.ai.dataagent.service.knowledge;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.knowledge.chunk.ParentChildChunk;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检索智能体知识，并把父子分块的子块展开成父块（"子块匹配、父块作答"，ADR-012）。
 * <ol>
 * <li>检索 {@code childTopK} 个子块；</li>
 * <li>结果里没有任何子块（全是 QA / FAQ 或旧切块方式的块）时，取前 {@code legacyTopK} 条直接返回，与原行为一致；</li>
 * <li>否则按父块分组（没有 parentId 的各自成组），组的分数取组内最高的子块分数，按分数降序依次取组， 直到组数达到 {@code maxParents}
 * 或总字数达到 {@code budgetChars}（第一组总会保留）；</li>
 * <li>选中的父块用 {@code parentId IN [...]} 查出全部子块，按 childSeq 拼回全文。</li>
 * </ol>
 */
@Slf4j
@Component
public class AgentKnowledgeRetriever {

	/** 一次展开的父块最多有多少个子块（远大于实际值，只是查询上限）。 */
	private static final int MAX_CHILDREN_PER_QUERY = 2000;

	private final AgentVectorStoreService vectorStoreService;

	private final DataAgentProperties properties;

	private final KnowledgeReranker reranker;

	@Autowired
	public AgentKnowledgeRetriever(AgentVectorStoreService vectorStoreService, DataAgentProperties properties) {
		this(vectorStoreService, properties, new KnowledgeReranker(properties.getAgentKnowledge().getRerank()));
	}

	/** 指定 reranker（测试、评测用）。 */
	public AgentKnowledgeRetriever(AgentVectorStoreService vectorStoreService, DataAgentProperties properties,
			KnowledgeReranker reranker) {
		this.vectorStoreService = vectorStoreService;
		this.properties = properties;
		this.reranker = reranker;
	}

	/**
	 * 按生产配置检索
	 */
	public List<Document> retrieve(String agentId, String query) {
		DataAgentProperties.VectorStoreProperties vs = properties.getVectorStore();
		DataAgentProperties.ParentChild pc = properties.getAgentKnowledge().getParentChild();
		return retrieve(agentId, query, pc.getChildTopK(), vs.getDefaultSimilarityThreshold(), vs.getDefaultTopkLimit(),
				pc.getMaxParents(), pc.getContextBudgetChars());
	}

	public List<Document> retrieve(String agentId, String query, int childTopK, double threshold, int legacyTopK,
			int maxParents, int budgetChars) {
		List<Document> hits = vectorStoreService.getDocumentsForAgent(agentId, query,
				DocumentMetadataConstant.AGENT_KNOWLEDGE, childTopK, threshold);
		boolean anyChild = false;
		for (Document d : hits) {
			anyChild |= d.getMetadata().get(DocumentMetadataConstant.PARENT_ID) != null;
		}
		if (!anyChild) {
			return hits.size() > legacyTopK ? new ArrayList<>(hits.subList(0, legacyTopK)) : hits;
		}
		// 分组：结果已按分数降序，组第一次出现时的子块就是组内最高分
		Map<String, Document> groups = new LinkedHashMap<>();
		for (Document d : hits) {
			Object parentId = d.getMetadata().get(DocumentMetadataConstant.PARENT_ID);
			String key = parentId == null ? "doc:" + d.getId() : "parent:" + parentId;
			groups.putIfAbsent(key, d);
		}
		// 开启精排时多取几个候选交给 reranker，否则直接取前 maxParents 个
		boolean rerank = reranker.isEnabled();
		int take = rerank ? Math.max(maxParents, properties.getAgentKnowledge().getRerank().getCandidates())
				: maxParents;
		List<Document> candidates = new ArrayList<>(groups.values());
		if (candidates.size() > take) {
			candidates = candidates.subList(0, take);
		}
		Map<String, String> parentTexts = loadParents(agentId, candidates);
		List<Document> docs = new ArrayList<>();
		for (Document best : candidates) {
			Object parentId = best.getMetadata().get(DocumentMetadataConstant.PARENT_ID);
			docs.add(parentId == null ? best : toParentDocument(best, parentTexts.get(parentId.toString())));
		}
		docs = rerank ? rerank(query, docs, maxParents) : docs;

		List<Document> result = new ArrayList<>();
		int used = 0;
		for (Document doc : docs) {
			int length = doc.getText() == null ? 0 : doc.getText().length();
			if (!result.isEmpty() && used + length > budgetChars) {
				break;
			}
			result.add(doc);
			used += length;
		}
		log.debug("Agent knowledge: {} hits -> {} groups -> {} returned ({} chars)", hits.size(), groups.size(),
				result.size(), used);
		return result;
	}

	/**
	 * 精排：按 reranker 分数降序（同分保持向量顺序），丢掉低于 minScore 的，最多留 min(maxResults, maxParents) 个。
	 * reranker 不可用时退回向量顺序的前 maxParents 个。
	 */
	private List<Document> rerank(String query, List<Document> docs, int maxParents) {
		if (docs.isEmpty()) {
			return docs;
		}
		List<String> texts = new ArrayList<>();
		for (Document d : docs) {
			texts.add(d.getText() == null ? "" : d.getText());
		}
		double[] scores = reranker.score(query, texts);
		if (scores == null) {
			return docs.size() > maxParents ? new ArrayList<>(docs.subList(0, maxParents)) : docs;
		}
		DataAgentProperties.Rerank config = properties.getAgentKnowledge().getRerank();
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < docs.size(); i++) {
			order.add(i);
		}
		order.sort((a, b) -> Double.compare(scores[b], scores[a]));
		int limit = Math.min(config.getMaxResults(), maxParents);
		List<Document> kept = new ArrayList<>();
		for (int i : order) {
			if (kept.size() >= limit || scores[i] < config.getMinScore()) {
				break;
			}
			Document d = docs.get(i);
			Map<String, Object> metadata = new HashMap<>(d.getMetadata());
			metadata.put(DocumentMetadataConstant.RERANK_SCORE, scores[i]);
			kept.add(Document.builder().id(d.getId()).text(d.getText()).metadata(metadata).score(d.getScore()).build());
		}
		log.debug("Rerank: {} candidates -> {} kept (minScore {})", docs.size(), kept.size(), config.getMinScore());
		return kept;
	}

	/** 查出候选父块的全部子块，拼回父块正文：parentId -> 正文。 */
	private Map<String, String> loadParents(String agentId, List<Document> candidates) {
		List<Object> parentIds = new ArrayList<>();
		for (Document d : candidates) {
			Object parentId = d.getMetadata().get(DocumentMetadataConstant.PARENT_ID);
			if (parentId != null) {
				parentIds.add(parentId.toString());
			}
		}
		Map<String, String> texts = new HashMap<>();
		if (parentIds.isEmpty()) {
			return texts;
		}
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		Filter.Expression filter = b.and(
				b.and(b.eq(Constant.AGENT_ID, agentId),
						b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE)),
				b.in(DocumentMetadataConstant.PARENT_ID, parentIds))
			.build();
		Map<String, List<Document>> children = new HashMap<>();
		for (Document d : vectorStoreService.getDocumentsOnlyByFilter(filter, MAX_CHILDREN_PER_QUERY)) {
			Object parentId = d.getMetadata().get(DocumentMetadataConstant.PARENT_ID);
			// 再按 parentId 精确过滤一次：不依赖向量库对 IN 的实现细节
			if (parentId != null && parentIds.contains(parentId.toString())) {
				children.computeIfAbsent(parentId.toString(), k -> new ArrayList<>()).add(d);
			}
		}
		for (Map.Entry<String, List<Document>> e : children.entrySet()) {
			List<Document> list = e.getValue();
			list.sort(Comparator
				.comparingInt(d -> ((Number) d.getMetadata().get(DocumentMetadataConstant.CHILD_SEQ)).intValue()));
			List<String> childTexts = new ArrayList<>();
			for (Document d : list) {
				childTexts.add(d.getText());
			}
			texts.put(e.getKey(), ParentChildChunk.restoreParent(childTexts));
		}
		return texts;
	}

	/** 父块文档：沿用最高分子块的 metadata 和分数，正文换成"【章节路径】+ 父块全文"。 */
	private static Document toParentDocument(Document best, String parentText) {
		Object section = best.getMetadata().get(DocumentMetadataConstant.SECTION);
		String body = parentText == null ? best.getText() : "【" + section + "】\n" + parentText;
		return Document.builder()
			.id(best.getMetadata().get(DocumentMetadataConstant.PARENT_ID).toString())
			.text(body)
			.metadata(new HashMap<>(best.getMetadata()))
			.score(best.getScore())
			.build();
	}

}
