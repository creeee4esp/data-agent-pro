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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.connector.ddl.DdlFactory;
import com.alibaba.cloud.ai.dataagent.connector.impls.mysql.MySQLDBAccessor;
import com.alibaba.cloud.ai.dataagent.connector.impls.mysql.MysqlJdbcConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.impls.mysql.MysqlJdbcDdl;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.dto.ModelConfigDTO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SchemaInitRequest;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import org.springframework.ai.chat.client.ChatClient;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.DynamicModelFactory;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.schema.TableMetadataService;
import com.alibaba.cloud.ai.dataagent.service.schemalink.index.CachingEmbeddingModel;
import com.alibaba.cloud.ai.dataagent.service.schemalink.index.FileEmbeddingCacheStore;
import com.alibaba.cloud.ai.dataagent.service.vector.MetadataDocumentRetriever;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.hybrid.fusion.impl.RrfFusionStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.HybridRetrievalStrategy;
import com.alibaba.cloud.ai.dataagent.service.hybrid.retrieval.impl.ElasticsearchHybridRetrievalStrategy;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.MetadataAwareSimpleVectorStore;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.StableElasticsearchVectorStore;
import com.alibaba.cloud.ai.dataagent.strategy.EnhancedTokenCountBatchingStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.Getter;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.elasticsearch.SimilarityFunction;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.env.StandardEnvironment;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 评测环境：按 ADR-009 手工装配召回链路需要的生产类，并用生产初始化路径（{@link SchemaServiceImpl#schema}）把 被评测数据库的
 * schema 写入内存向量库。
 * <p>
 * 不启动 Spring 容器，不依赖 saa_data_agent 业务库。用完调用 {@link #close()} 关闭线程池。
 */
public class BenchmarkEnvironment implements AutoCloseable {

	/** 内存向量库里的数据源 ID，只在本进程内有效，与业务库中的数据源 ID 无关。 */
	public static final int DATASOURCE_ID = 1;

	private final ExecutorService executor;

	/** 向量库实例（内存或 Elasticsearch）。 */
	private final VectorStore store;

	/** vectorStore=elasticsearch 时的连接和索引名，用完删除索引。 */
	private RestClient elasticsearchClient;

	private String elasticsearchIndex;

	private static final AtomicInteger INDEX_SEQ = new AtomicInteger();

	@Getter
	private final SchemaService schemaService;

	@Getter
	private final AgentVectorStoreService vectorStoreService;

	@Getter
	private final CachingEmbeddingModel embeddingModel;

	/** 对话模型（agentic 补全用）；未配置 benchmark.chat.model 时调用会抛异常，补全退回热启动。 */
	@Getter
	private final AiModelRegistry aiModelRegistry;

	/** 被评测数据库的连接配置。 */
	private final DbConfigBO dbConfig;

	/** 手工装配的 DatabaseUtil：任何智能体都指向被评测的数据库（explore_schema / verify_schema 用）。 */
	@Getter
	private final DatabaseUtil databaseUtil;

	/** 带缓存的对话模型，用于统计调用次数和 token；未配置时为 {@code null}。 */
	@Getter
	private final CachingChatModel chatModel;

	@Getter
	private final DataAgentProperties properties;

	/** 初始化了多少张表。 */
	@Getter
	private int tableCount;

	/** 初始化（向量化全部 schema 文档）耗时，毫秒。 */
	@Getter
	private long initMillis;

	/** 初始化阶段实际调用 Embedding 模型的文本数（未命中缓存的）。 */
	@Getter
	private long initEmbeddingCalls;

	private BenchmarkEnvironment(BenchmarkConfig config) {
		this.properties = new DataAgentProperties();
		if (!config.getCompletionTools().isEmpty()) {
			properties.getSchemaLinking()
				.setCompletionTools(new ArrayList<>(Arrays.asList(config.getCompletionTools().split("\\s*,\\s*"))));
		}

		// 1. Embedding：与生产相同，由 DynamicModelFactory 创建 OpenAI 兼容模型；provider=custom 时不要求
		// apiKey
		ModelConfigDTO modelConfig = new ModelConfigDTO();
		modelConfig.setProvider("custom");
		modelConfig.setBaseUrl(config.getEmbeddingBaseUrl());
		modelConfig.setModelName(config.getEmbeddingModel());
		EmbeddingModel remote = new DynamicModelFactory().createEmbeddingModel(modelConfig);
		this.embeddingModel = new CachingEmbeddingModel(remote, config.getEmbeddingModel(),
				FileEmbeddingCacheStore.forModel(config.getEmbeddingCacheDir(), config.getEmbeddingModel()));

		// 1b. 对话模型：与生产相同，由 DynamicModelFactory 创建；手工装配，不依赖业务库里的模型配置
		this.chatModel = chatModel(config);
		this.aiModelRegistry = chatModelRegistry(chatModel);

		this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));

		// 2. 向量库：与生产默认配置（spring.ai.vectorstore.type=simple）相同的实现
		this.store = createVectorStore(config, embeddingModel);
		// 混合检索（-Dbenchmark.hybrid=true，只支持 ES）：与生产 HybridRetrievalConfiguration 相同的装配
		Optional<HybridRetrievalStrategy> hybrid = Optional.empty();
		if (Boolean.getBoolean("benchmark.hybrid")) {
			if (elasticsearchIndex == null) {
				throw new IllegalStateException(
						"benchmark.hybrid=true requires -Dbenchmark.vector-store=elasticsearch");
			}
			properties.getVectorStore().setEnableHybridSearch(true);
			ElasticsearchHybridRetrievalStrategy strategy = new ElasticsearchHybridRetrievalStrategy(executor, store,
					new RrfFusionStrategy(properties.getVectorStore().getRrfK()),
					properties.getVectorStore().getHybridSearchTimeoutMs());
			strategy.setIndexName(elasticsearchIndex);
			strategy.setMinScore(properties.getVectorStore().getElasticsearchMinScore());
			hybrid = Optional.of(strategy);
		}
		AgentVectorStoreServiceImpl vectorStoreService = new AgentVectorStoreServiceImpl(store, hybrid, properties,
				new DynamicFilterService(null, null), new MetadataDocumentRetriever(new StandardEnvironment()));
		this.vectorStoreService = vectorStoreService;

		// 3. 数据库访问：只装配 MySQL 的 Ddl、连接池和 Accessor
		DdlFactory ddlFactory = new DdlFactory(Collections.singletonList(new MysqlJdbcDdl()));
		DBConnectionPoolFactory poolFactory = new DBConnectionPoolFactory(
				Collections.singletonList(new MysqlJdbcConnectionPool()));
		// MySQLDBAccessor 的构造器是 protected，跨包只能用匿名子类实例化（类体为空，行为与原类完全相同）
		AccessorFactory accessorFactory = new AccessorFactory(
				Collections.singletonList(new MySQLDBAccessor(ddlFactory, poolFactory) {
				}));

		this.dbConfig = DbConfigBO.builder()
			.url(config.getJdbcUrl())
			.username(config.getDbUser())
			.password(config.getDbPassword())
			.connectionType("jdbc")
			.dialectType("mysql")
			.schema(config.getSchemaName())
			.build();
		this.databaseUtil = new DatabaseUtil(accessorFactory, null, null) {
			@Override
			public DbConfigBO getAgentDbConfig(Long agentId) {
				return dbConfig;
			}

			@Override
			public Accessor getAgentAccessor(Long agentId) {
				return accessorFactory.getAccessorByDbConfig(dbConfig);
			}
		};

		// 4. SchemaServiceImpl：分批策略参数与 DataAgentConfiguration#customBatchingStrategy
		// 的默认值一致
		DataAgentProperties.EmbeddingBatch batch = properties.getEmbeddingBatch();
		this.schemaService = new SchemaServiceImpl(executor, accessorFactory,
				new TableMetadataService(accessorFactory, new ObjectMapper()),
				new EnhancedTokenCountBatchingStrategy(EncodingType.CL100K_BASE, batch.getMaxTokenCount(),
						batch.getReservePercentage(), batch.getMaxTextCount()),
				new DynamicFilterService(null, null), properties, vectorStoreService);
	}

	/**
	 * 装配并初始化：列出 schema 下的全部表，走生产初始化路径写入向量库。
	 * @throws IllegalStateException 数据库不可达，或初始化失败
	 */
	/** 未配置 benchmark.chat.model 时为 {@code null}。 */
	private static CachingChatModel chatModel(BenchmarkConfig config) {
		if (config.getChatModel().isEmpty()) {
			return null;
		}
		ModelConfigDTO chat = new ModelConfigDTO();
		chat.setProvider("custom");
		chat.setBaseUrl(config.getChatBaseUrl());
		chat.setModelName(config.getChatModel());
		chat.setApiKey(config.getChatApiKey());
		chat.setCompletionsPath(config.getChatCompletionsPath().isEmpty() ? null : config.getChatCompletionsPath());
		chat.setTemperature(0.0);
		// 推理模型（如 deepseek-flash）的思考也计入输出 token，额度太小时回答会为空
		chat.setMaxTokens(16000);
		String dirName = config.getChatModel().replaceAll("[^A-Za-z0-9._-]", "_");
		return new CachingChatModel(new DynamicModelFactory().createChatModel(chat), config.getChatModel(),
				config.getChatCacheDir().resolve(dirName), config.isChatCacheOnly());
	}

	/** 手工装配的 AiModelRegistry：只提供对话模型；未配置时调用会抛异常，补全退回热启动。 */
	private static AiModelRegistry chatModelRegistry(CachingChatModel chatModel) {
		ChatClient client = chatModel == null ? null : ChatClient.builder(chatModel).build();
		return new AiModelRegistry(null, null) {
			@Override
			public ChatClient getChatClient() {
				if (client == null) {
					throw new IllegalStateException("benchmark.chat.model is not configured");
				}
				return client;
			}
		};
	}

	public static BenchmarkEnvironment initialize(BenchmarkConfig config) throws Exception {
		List<String> tables = listTables(config);
		BenchmarkEnvironment env = new BenchmarkEnvironment(config);
		SchemaInitRequest request = new SchemaInitRequest();
		request.setDbConfig(env.dbConfig);
		request.setTables(tables);

		long start = System.nanoTime();
		Boolean ok = env.schemaService.schema(DATASOURCE_ID, request);
		env.refreshIndex();
		env.initMillis = (System.nanoTime() - start) / 1_000_000;
		if (!Boolean.TRUE.equals(ok)) {
			env.close();
			throw new IllegalStateException("Schema initialization returned " + ok);
		}
		env.tableCount = tables.size();
		env.initEmbeddingCalls = env.embeddingModel.getMissCount();
		return env;
	}

	/**
	 * 只装配、不初始化 schema：向量库为空，供知识检索评测自己写入知识。
	 */
	public static BenchmarkEnvironment createEmpty(BenchmarkConfig config) {
		return new BenchmarkEnvironment(config);
	}

	/** ES 写入后立即刷新，使文档可检索；内存向量库什么也不做。 */
	public void refresh() {
		refreshIndex();
	}

	/** 列出 schema 下的所有表；连不上数据库时给出明确的错误信息，而不是跳过测试。 */
	static List<String> listTables(BenchmarkConfig config) {
		String sql = "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? "
				+ "AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME";
		List<String> tables = new ArrayList<>();
		try (Connection connection = DriverManager.getConnection(config.getJdbcUrl(), config.getDbUser(),
				config.getDbPassword()); PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, config.getSchemaName());
			try (ResultSet rs = statement.executeQuery()) {
				while (rs.next()) {
					tables.add(rs.getString(1));
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("Cannot connect to benchmark database " + config.displayJdbcUrl()
					+ " (is MySQL running and the dataset generated?): " + e.getMessage(), e);
		}
		if (tables.isEmpty()) {
			throw new IllegalStateException(
					"No tables in schema " + config.getSchemaName() + "; generate it first with tools/dataset-gen");
		}
		return tables;
	}

	/**
	 * 向量库：默认进程内存；{@code -Dbenchmark.vector-store=elasticsearch} 时用 Spring AI 的
	 * ElasticsearchVectorStore （每个环境一个新索引，余弦相似度，1024 维；close 时删除）。
	 */
	private VectorStore createVectorStore(BenchmarkConfig config, EmbeddingModel embeddingModel) {
		if (!"elasticsearch".equalsIgnoreCase(config.getVectorStore())) {
			return new MetadataAwareSimpleVectorStore(embeddingModel);
		}
		elasticsearchClient = RestClient.builder(HttpHost.create(config.getElasticsearchUrl())).build();
		elasticsearchIndex = "benchmark-" + config.getSchemaName().toLowerCase().replace('_', '-') + "-"
				+ System.currentTimeMillis() + "-" + INDEX_SEQ.incrementAndGet();
		ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
		options.setIndexName(elasticsearchIndex);
		options.setDimensions(1024);
		options.setSimilarity(SimilarityFunction.cosine);
		// 生产同款：候选数放大 + 打平时确定性排序
		ElasticsearchVectorStore es = new StableElasticsearchVectorStore(
				ElasticsearchVectorStore.builder(elasticsearchClient, embeddingModel), options, embeddingModel, true);
		try {
			es.afterPropertiesSet();
		}
		catch (Exception e) {
			throw new IllegalStateException("Cannot create Elasticsearch index at " + config.getElasticsearchUrl()
					+ " (is Elasticsearch running?): " + e.getMessage(), e);
		}
		return es;
	}

	/** ES 写入后默认约 1 秒才能检索到；初始化完立即刷新，评测才看得到全部文档。 */
	private void refreshIndex() {
		if (elasticsearchClient == null) {
			return;
		}
		try {
			elasticsearchClient.performRequest(new Request("POST", "/" + elasticsearchIndex + "/_refresh"));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public void close() {
		executor.shutdownNow();
		if (elasticsearchClient != null) {
			try {
				elasticsearchClient.performRequest(new Request("DELETE", "/" + elasticsearchIndex));
				elasticsearchClient.close();
			}
			catch (IOException e) {
				// 删除索引失败不影响评测结果
			}
		}
	}

}
