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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 调用 reranker 服务给（问题, 文本）打相关度分（交叉编码器，例如 Qwen3-Reranker，见 tools/reranker）。
 * <p>
 * 接口：{@code POST {"query": ..., "texts": [...]}} → {@code [{"index": i, "score": s},
 * ...]}，与 TEI / Jina 的 rerank 接口形状一致。服务不可用时返回 {@code null}，由调用方退回向量顺序。
 */
@Slf4j
public class KnowledgeReranker {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final DataAgentProperties.Rerank config;

	private final HttpClient client;

	public KnowledgeReranker(DataAgentProperties.Rerank config) {
		this.config = config;
		// 本地服务，不走系统代理
		this.client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
			.proxy(HttpClient.Builder.NO_PROXY)
			.build();
	}

	public boolean isEnabled() {
		return config.isEnabled();
	}

	/**
	 * @return 与 {@code texts} 一一对应的分数；服务失败时为 {@code null}
	 */
	public double[] score(String query, List<String> texts) {
		try {
			Map<String, Object> body = new HashMap<>();
			body.put("query", query);
			body.put("texts", texts);
			HttpRequest request = HttpRequest.newBuilder(URI.create(config.getUrl()))
				.timeout(Duration.ofMillis(config.getTimeoutMs()))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
				.build();
			HttpResponse<String> response = client.send(request,
					HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() != 200) {
				log.warn("Reranker returned HTTP {}; falling back to vector order", response.statusCode());
				return null;
			}
			double[] scores = new double[texts.size()];
			for (JsonNode item : MAPPER.readTree(response.body())) {
				scores[item.get("index").asInt()] = item.get("score").asDouble();
			}
			return scores;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
		catch (Exception e) {
			log.warn("Reranker call failed ({}); falling back to vector order", e.toString());
			return null;
		}
	}

}
