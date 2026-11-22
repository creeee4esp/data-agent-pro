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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class CachingChatModelTest {

	@TempDir
	Path dir;

	@Test
	void secondIdenticalCallIsServedFromDiskWithRecordedUsage() {
		ChatModel delegate = mock(ChatModel.class);
		ChatResponse response = new ChatResponse(
				Collections.singletonList(new Generation(new AssistantMessage("{\"tables\": [\"a\"]}"))),
				ChatResponseMetadata.builder().usage(new DefaultUsage(100, 20)).build());
		when(delegate.call(any(Prompt.class))).thenReturn(response);

		CachingChatModel first = new CachingChatModel(delegate, "m", dir);
		assertEquals("{\"tables\": [\"a\"]}", first.call(new Prompt("q")).getResult().getOutput().getText());
		// 新实例（模拟重跑）也能命中磁盘缓存
		CachingChatModel second = new CachingChatModel(delegate, "m", dir);
		assertEquals("{\"tables\": [\"a\"]}", second.call(new Prompt("q")).getResult().getOutput().getText());

		verify(delegate, times(1)).call(any(Prompt.class));
		assertEquals(1, first.getMisses());
		assertEquals(0, second.getMisses());
		assertEquals(100, second.getPromptTokens());
		assertEquals(20, second.getCompletionTokens());
	}

	@Test
	void cacheOnlyModeFailsOnMissWithoutCallingTheModel() {
		ChatModel delegate = mock(ChatModel.class);
		CachingChatModel model = new CachingChatModel(delegate, "m", dir, true);
		assertThrows(IllegalStateException.class, () -> model.call(new Prompt("q")));
		verify(delegate, times(0)).call(any(Prompt.class));
	}

	@Test
	void keyDependsOnModelAndMessages() {
		CachingChatModel a = new CachingChatModel(mock(ChatModel.class), "m1", dir);
		CachingChatModel b = new CachingChatModel(mock(ChatModel.class), "m2", dir);
		assertEquals(a.key(new Prompt("q")), a.key(new Prompt("q")));
		assertNotEquals(a.key(new Prompt("q")), a.key(new Prompt("q2")));
		assertNotEquals(a.key(new Prompt("q")), b.key(new Prompt("q")));
	}

}
