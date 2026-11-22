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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 评测用的对话模型缓存：请求相同（模型名 + 全部消息）时直接返回上次的回答，保证可复现、重跑不计费。
 * <p>
 * 工具调用的循环在被包装模型的一次 {@link #call} 内完成，所以一条缓存对应一次完整的补全；命中时工具不会再执行。 每条缓存是
 * {@code <cacheDir>/<sha256>.json}，内容为回答文本和（累计的）token 用量。
 */
class CachingChatModel implements ChatModel {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final ChatModel delegate;

	private final String modelName;

	private final Path cacheDir;

	/** 只读缓存：未命中时直接报错，不调用模型（用来保证一次评测不花 token）。 */
	private final boolean cacheOnly;

	/** 调用次数 / 未命中次数。 */
	@Getter
	private long calls;

	@Getter
	private long misses;

	/** 全部调用的 token 用量（命中时取缓存里记录的值，即"不缓存时的成本"）。 */
	@Getter
	private long promptTokens;

	@Getter
	private long completionTokens;

	CachingChatModel(ChatModel delegate, String modelName, Path cacheDir) {
		this(delegate, modelName, cacheDir, false);
	}

	CachingChatModel(ChatModel delegate, String modelName, Path cacheDir, boolean cacheOnly) {
		this.delegate = delegate;
		this.modelName = modelName;
		this.cacheDir = cacheDir;
		this.cacheOnly = cacheOnly;
	}

	@Override
	public synchronized ChatResponse call(Prompt prompt) {
		calls++;
		Path file = cacheDir.resolve(key(prompt) + ".json");
		try {
			String content;
			long in;
			long out;
			if (Files.exists(file)) {
				JsonNode node = MAPPER.readTree(file.toFile());
				content = node.path("content").asText();
				in = node.path("promptTokens").asLong();
				out = node.path("completionTokens").asLong();
			}
			else {
				misses++;
				if (cacheOnly) {
					throw new IllegalStateException("Chat cache miss in cache-only mode: " + file.getFileName());
				}
				ChatResponse response = delegate.call(prompt);
				content = response.getResult() == null ? "" : response.getResult().getOutput().getText();
				Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
				in = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
				out = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("model", modelName);
				entry.put("content", content);
				entry.put("promptTokens", in);
				entry.put("completionTokens", out);
				// 空回答不缓存：推理模型的思考用完 max_tokens 时 content 为空（finish_reason=length），下次应重新请求
				if (content != null && !content.trim().isEmpty()) {
					Files.createDirectories(cacheDir);
					MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), entry);
				}
			}
			promptTokens += in;
			completionTokens += out;
			return new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(content))));
		}
		catch (IOException e) {
			throw new UncheckedIOException("Cannot read or write chat cache " + file, e);
		}
	}

	/**
	 * 流式调用也走缓存：内部调用 {@link #call}，把完整回答包成只有一个元素的 Flux。
	 * <p>
	 * 生产代码的 fineSelect、generateSql 走 stream()；评测只关心最终文本，不需要逐字输出。
	 */
	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.defer(() -> Flux.just(call(prompt)));
	}

	/** 必须透传：ChatClient 从这里复制选项，工具调用依赖它是 ToolCallingChatOptions。 */
	@Override
	public ChatOptions getDefaultOptions() {
		return delegate.getDefaultOptions();
	}

	String key(Prompt prompt) {
		StringBuilder sb = new StringBuilder(modelName).append('\n');
		for (Message message : prompt.getInstructions()) {
			sb.append(message.getMessageType()).append(':').append(message.getText()).append('\n');
		}
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
