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
package com.alibaba.cloud.ai.dataagent.service.schemalink.index;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * 测试缓存策略。存储用内存实现，测试不碰磁盘；文件存储在 FileEmbeddingCacheStoreTest 中单独测试。
 */
@ExtendWith(MockitoExtension.class)
class CachingEmbeddingModelTest {

	private static final String MODEL = "qwen3-embedding:0.6b";

	@Mock
	private EmbeddingModel delegate;

	private final InMemoryEmbeddingCacheStore store = new InMemoryEmbeddingCacheStore();

	@Test
	void embed_secondCall_hitsCacheWithoutCallingDelegate() {
		stubDelegateWithFakeVectors(delegate);
		CachingEmbeddingModel model = new CachingEmbeddingModel(delegate, MODEL, store);

		float[] first = model.embed("订单金额");
		float[] second = model.embed("订单金额");

		assertArrayEquals(first, second);
		verify(delegate, times(1)).call(any(EmbeddingRequest.class));
		assertEquals(1, model.getMissCount());
		assertEquals(1, model.getHitCount());
	}

	@Test
	void call_batchWithPartialHits_requestsOnlyMissesAndKeepsOrder() {
		stubDelegateWithFakeVectors(delegate);
		CachingEmbeddingModel model = new CachingEmbeddingModel(delegate, MODEL, store);
		model.embed("b"); // 先让 b 进入缓存

		List<float[]> vectors = model.embed(Arrays.asList("a", "b", "cc"));

		ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
		verify(delegate, times(2)).call(captor.capture());
		// 第二次请求只包含未命中的 a 和 cc
		assertEquals(Arrays.asList("a", "cc"), captor.getAllValues().get(1).getInstructions());
		// 返回结果与输入顺序一致：假向量 = [文本长度, 首字符]
		assertArrayEquals(fakeVector("a"), vectors.get(0));
		assertArrayEquals(fakeVector("b"), vectors.get(1));
		assertArrayEquals(fakeVector("cc"), vectors.get(2));
		assertEquals(3, model.getMissCount()); // b、a、cc
		assertEquals(1, model.getHitCount()); // 第二次调用里的 b
	}

	@Test
	void call_duplicateTextsInOneRequest_requestedOnce() {
		stubDelegateWithFakeVectors(delegate);
		CachingEmbeddingModel model = new CachingEmbeddingModel(delegate, MODEL, store);

		List<float[]> vectors = model.embed(Arrays.asList("x", "x", "y"));

		ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
		verify(delegate).call(captor.capture());
		assertEquals(Arrays.asList("x", "y"), captor.getValue().getInstructions());
		assertEquals(3, vectors.size());
		assertArrayEquals(fakeVector("x"), vectors.get(0));
		assertArrayEquals(fakeVector("x"), vectors.get(1));
		assertEquals(2, store.size());
	}

	@Test
	void call_allHits_doesNotCallDelegate() {
		stubDelegateWithFakeVectors(delegate);
		CachingEmbeddingModel warmUp = new CachingEmbeddingModel(delegate, MODEL, store);
		warmUp.embed(Arrays.asList("p", "q"));

		// 换一个从未被调用的 mock：全部命中时不应碰它
		EmbeddingModel unused = mock(EmbeddingModel.class);
		List<float[]> vectors = new CachingEmbeddingModel(unused, MODEL, store).embed(Arrays.asList("q", "p"));

		assertArrayEquals(fakeVector("q"), vectors.get(0));
		assertArrayEquals(fakeVector("p"), vectors.get(1));
		verifyNoInteractions(unused);
	}

	@Test
	void cacheKey_includesModelName() {
		CachingEmbeddingModel modelA = new CachingEmbeddingModel(delegate, "model-a", store);
		CachingEmbeddingModel modelB = new CachingEmbeddingModel(delegate, "model-b", store);

		assertNotEquals(modelA.cacheKey("同一段文本"), modelB.cacheKey("同一段文本"));
		assertEquals(64, modelA.cacheKey("同一段文本").length()); // SHA-256 = 32 字节 = 64
		// 个十六进制字符
	}

	@Test
	void persistsAcrossInstances_withFileStore(@TempDir Path cacheRoot) {
		// 和真实的文件存储串起来：第一个实例写入，第二个实例（新的 store 对象、新的 mock）直接从磁盘读到
		stubDelegateWithFakeVectors(delegate);
		new CachingEmbeddingModel(delegate, MODEL, FileEmbeddingCacheStore.forModel(cacheRoot, MODEL)).embed("用户表");

		EmbeddingModel freshDelegate = mock(EmbeddingModel.class);
		CachingEmbeddingModel reopened = new CachingEmbeddingModel(freshDelegate, MODEL,
				FileEmbeddingCacheStore.forModel(cacheRoot, MODEL));

		assertArrayEquals(fakeVector("用户表"), reopened.embed("用户表"));
		verifyNoInteractions(freshDelegate);
	}

	@Test
	void embedDocument_usesDocumentText() {
		stubDelegateWithFakeVectors(delegate);
		CachingEmbeddingModel model = new CachingEmbeddingModel(delegate, MODEL, store);

		assertArrayEquals(fakeVector("orders 表"), model.embed(new Document("orders 表")));
	}

	@Test
    void dimensions_delegates() {
        when(delegate.dimensions()).thenReturn(1024);

        assertEquals(1024, new CachingEmbeddingModel(delegate, MODEL, store).dimensions());
    }

	/** 假向量：[文本长度, 首字符编码]，不同文本得到不同向量，方便断言结果对应关系。 */
	private static float[] fakeVector(String text) {
		return new float[] { text.length(), text.charAt(0) };
	}

	/** 让 mock 按请求里的文本逐个返回假向量。 */
    private static void stubDelegateWithFakeVectors(EmbeddingModel mock) {
        when(mock.call(any(EmbeddingRequest.class))).thenAnswer(invocation -> {
            EmbeddingRequest request = invocation.getArgument(0);
            List<Embedding> embeddings = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                embeddings.add(new Embedding(fakeVector(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        });
    }

}
