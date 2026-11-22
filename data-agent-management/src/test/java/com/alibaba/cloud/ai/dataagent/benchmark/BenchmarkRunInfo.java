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
package com.alibaba.cloud.ai.dataagent.benchmark;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Value;

/**
 * 一次评测运行的背景信息：评测了什么、用什么配置、花了多少资源。写在报告开头，便于事后复现和比较两份报告。
 */
@Value
@Builder
public class BenchmarkRunInfo {

	/** 评测名，例如 legacy-baseline。 */
	String label;

	/** 被评测的 SchemaLinker 实现名（LinkDiagnostics.linkerName）。 */
	String linkerName;

	/** 开始时间，同时用于生成报告目录名。 */
	LocalDateTime startedAt;

	/** 数据库连接串（不含参数和密码）。 */
	String dataset;

	/** 初始化的表数量。 */
	int tableCount;

	/** 题集文件路径。 */
	String questionsFile;

	int questionCount;

	List<Integer> ks;

	String embeddingModel;

	/** 表召回的 topK 与相似度阈值（DataAgentProperties 中的值，与生产默认一致）。 */
	int tableTopK;

	double tableSimilarityThreshold;

	/** schema 初始化耗时，毫秒。 */
	long initMillis;

	/** 初始化阶段实际调用模型的文本数。 */
	long initEmbeddingCalls;

	/** 召回阶段（逐题提问）实际调用模型的文本数。 */
	long queryEmbeddingCalls;

	/** 平均每题输出的表数（agentic 补全输出的是集合，靠多选表刷分时这个数会变大）。 */
	double avgTables;

	/** 对话模型名；未使用时为 null。 */
	String chatModel;

	/** 对话模型调用次数、未命中缓存次数、token 用量（含命中缓存的，即不缓存时的成本）。 */
	long chatCalls;

	long chatMisses;

	long promptTokens;

	long completionTokens;

	/** 可复现性检查结果：true 一致、false 不一致、null 未检查。 */
	Boolean deterministic;

}
