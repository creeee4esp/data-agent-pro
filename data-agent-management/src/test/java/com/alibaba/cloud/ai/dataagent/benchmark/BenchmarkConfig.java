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

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 评测参数。每一项都可以用 JVM 系统属性（{@code -Dbenchmark.xxx=...}）覆盖，不传则使用本地开发环境的默认值。
 * <p>
 * 数据库密码优先读环境变量 {@code BENCHMARK_DB_PASSWORD}，避免出现在命令行历史里。
 */
@Value
@Builder
public class BenchmarkConfig {

	/** 被评测的 SchemaLinker 实现名，见 SchemaLinkerRegistry；取值与生产配置 schema-linking.mode 一致。 */
	String linker;

	/** 本次评测的名字，会出现在报告目录名和报告标题里；不配置时等于实现名。 */
	String label;

	/** 是否用第二个独立环境重跑一遍，检查逐题结果完全一致（可复现性）。 */
	boolean verifyDeterminism;

	/** 被评测的数据库（分析用的业务数据，不是 saa_data_agent）。 */
	String jdbcUrl;

	String dbUser;

	String dbPassword;

	/** 数据库名（MySQL 的 schema），用于列出要初始化的表。 */
	String schemaName;

	/** 题集文件。 */
	Path questionsFile;

	/** 要统计的 K 值。 */
	List<Integer> ks;

	/** OpenAI 兼容的 Embedding 服务地址，本地 Ollama 为 http://localhost:11434。 */
	String embeddingBaseUrl;

	String embeddingModel;

	/** OpenAI 兼容的对话模型服务地址（agentic 补全用）；API key 读环境变量 BENCHMARK_CHAT_API_KEY。 */
	String chatBaseUrl;

	/** 对话模型名；为空时不装配对话模型。 */
	String chatModel;

	String chatApiKey;

	/** 附加到 base-url 后的对话接口路径；为空时用 Spring AI 默认的 /v1/chat/completions。 */
	String chatCompletionsPath;

	/** 只用缓存：未命中时报错而不调用模型（-Dbenchmark.chat.cache-only=true）。 */
	boolean chatCacheOnly;

	/** 对话模型响应缓存根目录（按模型名分子目录）。 */
	Path chatCacheDir;

	/** agentic 补全启用的工具，逗号分隔；为空时用生产默认值（全部启用）。 */
	String completionTools;

	/** 向量库：simple（进程内存，默认）或 elasticsearch。 */
	String vectorStore;

	/** Elasticsearch 地址（vectorStore=elasticsearch 时用）。 */
	String elasticsearchUrl;

	/** Embedding 缓存根目录。放在 target 下，mvn clean 时会一起清掉。 */
	Path embeddingCacheDir;

	/** 报告输出根目录。 */
	Path reportDir;

	/**
	 * 从系统属性和环境变量读取参数。
	 * @param props 通常传 {@code System.getProperties()}，测试时可以传自定义 Map
	 * @param env 通常传 {@code System.getenv()}
	 */
	public static BenchmarkConfig from(Map<?, ?> props, Map<String, String> env) {
		Function<String, String> get = key -> {
			Object value = props.get("benchmark." + key);
			return value == null ? null : value.toString();
		};
		String password = env.get("BENCHMARK_DB_PASSWORD");
		if (password == null) {
			password = orDefault(get.apply("db.password"), "root");
		}
		String linker = orDefault(get.apply("linker"), "legacy");
		String schema = orDefault(get.apply("schema"), "enterprise_dw");
		return BenchmarkConfig.builder()
			.linker(linker)
			.label(orDefault(get.apply("label"), linker))
			.verifyDeterminism(Boolean.parseBoolean(orDefault(get.apply("verify-determinism"), "true")))
			// 默认连接串由 schema 拼出来：命令行只需传 -Dbenchmark.schema=xxx。
			// 不要在 Windows 命令行传带 & 的 jdbc.url：mvnw.cmd 会把 & 当成命令分隔符，后面的参数全部丢失
			.jdbcUrl(orDefault(get.apply("jdbc.url"),
					"jdbc:mysql://127.0.0.1:3306/" + schema + "?useUnicode=true&characterEncoding=utf-8"
							+ "&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai"))
			.dbUser(orDefault(get.apply("db.user"), "root"))
			.dbPassword(password)
			.schemaName(schema)
			// 测试运行时的工作目录是 data-agent-management 模块目录
			.questionsFile(Paths.get(orDefault(get.apply("questions"), "../tools/dataset-gen/questions/m0.jsonl")))
			.ks(parseKs(orDefault(get.apply("ks"), "5,10,20")))
			.embeddingBaseUrl(orDefault(get.apply("embedding.base-url"), "http://localhost:11434"))
			.embeddingModel(orDefault(get.apply("embedding.model"), "qwen3-embedding:0.6b"))
			.chatBaseUrl(orDefault(get.apply("chat.base-url"), "http://localhost:11434"))
			.chatModel(orDefault(get.apply("chat.model"), ""))
			.chatApiKey(orDefault(env.get("BENCHMARK_CHAT_API_KEY"), ""))
			.chatCompletionsPath(orDefault(get.apply("chat.completions-path"), ""))
			.chatCacheOnly(Boolean.parseBoolean(orDefault(get.apply("chat.cache-only"), "false")))
			.chatCacheDir(Paths.get(orDefault(get.apply("chat.cache-dir"), "target/llm-cache")))
			.completionTools(orDefault(get.apply("completion-tools"), ""))
			.vectorStore(orDefault(get.apply("vector-store"), "simple"))
			.elasticsearchUrl(orDefault(get.apply("elasticsearch.url"), "http://127.0.0.1:9200"))
			.embeddingCacheDir(Paths.get(orDefault(get.apply("embedding.cache-dir"), "target/embedding-cache")))
			.reportDir(Paths.get(orDefault(get.apply("report-dir"), "target/schema-benchmark")))
			.build();
	}

	/** 报告里展示的连接串：去掉 ? 后面的参数，不含账号密码。 */
	public String displayJdbcUrl() {
		int q = jdbcUrl.indexOf('?');
		return q < 0 ? jdbcUrl : jdbcUrl.substring(0, q);
	}

	static List<Integer> parseKs(String text) {
		List<Integer> result = new ArrayList<>();
		for (String part : text.split(",")) {
			int k = Integer.parseInt(part.trim());
			if (k <= 0) {
				throw new IllegalArgumentException("K must be positive: " + k);
			}
			result.add(k);
		}
		Collections.sort(result);
		return Collections.unmodifiableList(result);
	}

	private static String orDefault(String value, String defaultValue) {
		return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
	}

}
